/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package strideauth

import base.SpecBase
import config.FrontendAppConfig
import models.StrideAuthUser
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatest.matchers.should.Matchers.{should, shouldBe}
import play.api.http.Status
import play.api.mvc.*
import play.api.test.FakeRequest
import services.StrideEnrolmentServiceAlgebra
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name, ~}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class StrideAuthSpec extends SpecBase {

  private val strideUser =
    StrideAuthUser(
      credentials = Credentials(
        providerId = "12345",
        providerType = "PrivilegedApplication"
      ),
      email = "test@example.com",
      enrolments = Enrolments(
        Set(
          Enrolment("sdec_integration_tester")
        )
      ),
      name = Name(
        Some("Test User"),
        None
      )
    )

  type StrideRetrieval =
    ~[
      ~[
        ~[
          Enrolments,
          Option[String]
        ],
        Option[Credentials]
      ],
      Option[Name]
    ]

  private def successfulRetrieval: StrideRetrieval =
    new ~(
      new ~(
        new ~(
          strideUser.enrolments,
          Some(strideUser.email)
        ),
        Some(strideUser.credentials)
      ),
      Some(strideUser.name)
    )

  private val config = mock(classOf[FrontendAppConfig])
  when(config.appName).thenReturn("sdec-internal-frontend")
  when(config.loginUrl).thenReturn("http://localhost:9041/stride/sign-in")
  when(config.loginContinueUrl).thenReturn("localhost:4000/sdec-admin")

  private def strideAuth(
      authConnector: AuthConnector,
      application: play.api.Application,
      strideEnrolmentService: StrideEnrolmentServiceAlgebra,
      config: FrontendAppConfig
  ): StrideAuth = {

    val actionBuilder =
      application.injector.instanceOf[DefaultActionBuilder]

    new StrideAuth(
      authConnector,
      application.environment,
      config,
      actionBuilder,
      strideEnrolmentService
    )
  }

  "StrideAuth" - {

    "execute the supplied action when authentication and enrolment verification succeed" in {

      val authConnector =
        mock(classOf[AuthConnector])

      val strideEnrolmentService =
        mock(classOf[StrideEnrolmentServiceAlgebra])

      when(
        authConnector.authorise[StrideRetrieval](
          any(),
          any()
        )(any(), any())
      ).thenReturn(
        Future.successful(successfulRetrieval)
      )

      when(
        strideEnrolmentService.extractSdecEnrolments(
          any()
        )
      ).thenReturn(
        strideUser.enrolments
      )

      when(
        strideEnrolmentService.verifyEnrollment(
          any()
        )
      ).thenReturn(
        Future.successful(true)
      )

      val app =
        applicationBuilder().build()

      val auth =
        strideAuth(
          authConnector,
          app,
          strideEnrolmentService,
          config
        )

      val action =
        (
            user: StrideAuthUser,
            _: Request[AnyContent]
        ) =>
          Future.successful(
            Results.Ok(user.email)
          )

      val request =
        FakeRequest(
          "GET",
          "/test"
        )

      val result =
        auth
          .authorisedFromStride(action)
          .apply(request)
          .futureValue

      result.header.status shouldBe Status.OK

      verify(strideEnrolmentService).extractSdecEnrolments(
        strideUser.enrolments
      )

      verify(strideEnrolmentService).verifyEnrollment(
        strideUser.copy(
          enrolments = strideUser.enrolments
        )
      )
    }

    "redirect to insufficient roles when enrolment verification returns false" in {

      val authConnector =
        mock(classOf[AuthConnector])

      val strideEnrolmentService =
        mock(classOf[StrideEnrolmentServiceAlgebra])

      when(
        authConnector.authorise[StrideRetrieval](
          any(),
          any()
        )(any(), any())
      ).thenReturn(
        Future.successful(successfulRetrieval)
      )

      when(
        strideEnrolmentService.extractSdecEnrolments(
          any()
        )
      ).thenReturn(
        strideUser.enrolments
      )

      when(
        strideEnrolmentService.verifyEnrollment(
          any()
        )
      ).thenReturn(
        Future.successful(false)
      )

      val app =
        applicationBuilder().build()

      val auth =
        strideAuth(
          authConnector,
          app,
          strideEnrolmentService,
          config
        )

      val action =
        (
            _: StrideAuthUser,
            _: Request[AnyContent]
        ) =>
          Future.failed(
            new AssertionError(
              "The supplied action should not have been executed"
            )
          )

      val request =
        FakeRequest(
          "GET",
          "/test"
        )

      val result =
        auth
          .authorisedFromStride(action)
          .apply(request)
          .futureValue

      result.header.status shouldBe Status.SEE_OTHER

      result.header.headers("Location") shouldBe
        controllers.routes.InsufficientRolesController.get.url

      verify(strideEnrolmentService).verifyEnrollment(
        any()
      )
    }

    "redirect to STRIDE when there is no active session" in {

      val authConnector =
        mock(classOf[AuthConnector])

      val strideEnrolmentService =
        mock(classOf[StrideEnrolmentServiceAlgebra])

      when(
        authConnector.authorise[StrideRetrieval](
          any(),
          any()
        )(any(), any())
      ).thenReturn(
        Future.failed(
          new NoActiveSession("No active session") {}
        )
      )

      val app =
        applicationBuilder().build()

      val auth =
        strideAuth(
          authConnector,
          app,
          strideEnrolmentService,
          config
        )

      val action =
        (
            _: StrideAuthUser,
            _: Request[AnyContent]
        ) =>
          Future.failed(
            new AssertionError(
              "The supplied action should not have been executed"
            )
          )

      val request =
        FakeRequest(
          "GET",
          "/test"
        )

      val result =
        auth
          .authorisedFromStride(action)
          .apply(request)
          .futureValue

      result.header.status shouldBe Status.SEE_OTHER

      val location =
        result.header.headers("Location")

      location should startWith(
        "http://localhost:9041/stride/sign-in?"
      )

      location should include(
        "successURL="
      )

      location should include(
        "origin=sdec"
      )

      location should not include "failureURL="

      verifyNoInteractions(strideEnrolmentService)
    }

    "redirect to the insufficient roles page when authentication returns insufficient enrolments" in {

      val authConnector =
        mock(classOf[AuthConnector])

      val strideEnrolmentService =
        mock(classOf[StrideEnrolmentServiceAlgebra])

      when(
        authConnector.authorise[StrideRetrieval](
          any(),
          any()
        )(any(), any())
      ).thenReturn(
        Future.failed(
          InsufficientEnrolments(
            "Insufficient enrolments"
          )
        )
      )

      val app =
        applicationBuilder().build()

      val auth =
        strideAuth(
          authConnector,
          app,
          strideEnrolmentService,
          config
        )

      val action =
        (
            _: StrideAuthUser,
            _: Request[AnyContent]
        ) =>
          Future.failed(
            new AssertionError(
              "The supplied action should not have been executed"
            )
          )

      val request =
        FakeRequest(
          "GET",
          "/test"
        )

      val result =
        auth
          .authorisedFromStride(action)
          .apply(request)
          .futureValue

      result.header.status shouldBe Status.SEE_OTHER

      result.header.headers("Location") shouldBe
        controllers.routes.InsufficientRolesController.get.url

      verifyNoInteractions(strideEnrolmentService)
    }

    "not execute the supplied action when there is no active session" in {

      val authConnector =
        mock(classOf[AuthConnector])

      val strideEnrolmentService =
        mock(classOf[StrideEnrolmentServiceAlgebra])

      when(
        authConnector.authorise[StrideRetrieval](
          any(),
          any()
        )(any(), any())
      ).thenReturn(
        Future.failed(
          new NoActiveSession("No active session") {}
        )
      )

      val app =
        applicationBuilder().build()

      val auth =
        strideAuth(
          authConnector,
          app,
          strideEnrolmentService,
          config
        )

      val action =
        (
            _: StrideAuthUser,
            _: Request[AnyContent]
        ) =>
          Future.failed(
            new AssertionError(
              "The supplied action should not have been executed"
            )
          )

      val request =
        FakeRequest(
          "GET",
          "/test"
        )

      val result =
        auth
          .authorisedFromStride(action)
          .apply(request)
          .futureValue

      result.header.status shouldBe Status.SEE_OTHER

      verifyNoInteractions(strideEnrolmentService)
    }

    "not execute the supplied action when authentication returns insufficient enrolments" in {

      val authConnector =
        mock(classOf[AuthConnector])

      val strideEnrolmentService =
        mock(classOf[StrideEnrolmentServiceAlgebra])

      when(
        authConnector.authorise[StrideRetrieval](
          any(),
          any()
        )(any(), any())
      ).thenReturn(
        Future.failed(
          InsufficientEnrolments(
            "Insufficient enrolments"
          )
        )
      )

      val app =
        applicationBuilder().build()

      val auth =
        strideAuth(
          authConnector,
          app,
          strideEnrolmentService,
          config
        )

      val action =
        (
            _: StrideAuthUser,
            _: Request[AnyContent]
        ) =>
          Future.failed(
            new AssertionError(
              "The supplied action should not have been executed"
            )
          )

      val request =
        FakeRequest(
          "GET",
          "/test"
        )

      val result =
        auth
          .authorisedFromStride(action)
          .apply(request)
          .futureValue

      result.header.status shouldBe Status.SEE_OTHER

      verifyNoInteractions(strideEnrolmentService)
    }
  }
}
