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
import models.StrideAuthUser
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatest.matchers.should.Matchers.{should, shouldBe}
import play.api.Configuration
import play.api.http.Status
import play.api.mvc.*
import play.api.test.FakeRequest
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

  private def strideAuth(
      authConnector: AuthConnector,
      application: play.api.Application,
      config: Configuration = Configuration.from(
        Map(
          "appName"          -> "sdec",
          "stride.role"      -> "sdec_integration_tester",
          "urls.strideLogin" -> "http://localhost:9041/stride/sign-in"
        )
      )
  ): StrideAuth = {

    val actionBuilder =
      application.injector.instanceOf[DefaultActionBuilder]

    new StrideAuth(
      authConnector,
      application.environment,
      config,
      actionBuilder
    )
  }

  "StrideAuth" - {

    "execute the supplied action when authentication and authorisation succeed" in {

      val authConnector =
        mock(classOf[AuthConnector])

      when(
        authConnector.authorise[StrideRetrieval](
          any(),
          any()
        )(any(), any())
      ).thenReturn(
        Future.successful(successfulRetrieval)
      )

      val app =
        applicationBuilder().build()

      val auth =
        strideAuth(
          authConnector,
          app
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
    }

    "redirect to STRIDE when there is no active session" in {

      val authConnector =
        mock(classOf[AuthConnector])

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
          app
        )

      val action =
        (
            _: StrideAuthUser,
            _: Request[AnyContent]
        ) =>
          Future.successful(
            Results.Ok("success")
          )

      val request =
        FakeRequest(
          "GET",
          "/test"
        ).withHeaders(
          "Host" -> "localhost"
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
    }

    "redirect to the insufficient roles page when the user is not sufficiently enrolled" in {

      val authConnector =
        mock(classOf[AuthConnector])

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
          app
        )

      val action =
        (
            _: StrideAuthUser,
            _: Request[AnyContent]
        ) =>
          Future.successful(
            Results.Ok("success")
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

      location shouldBe
        controllers.routes.InsufficientRolesController.get.url
    }

    "not execute the supplied action when there is no active session" in {

      val authConnector =
        mock(classOf[AuthConnector])

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
          app
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
    }

    "not execute the supplied action when the user has insufficient enrolments" in {

      val authConnector =
        mock(classOf[AuthConnector])

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
          app
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
    }
  }
}
