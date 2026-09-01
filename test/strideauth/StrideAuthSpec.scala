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
import models.TestStrideAuthUser
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.mvc.DefaultActionBuilder
import play.api.mvc.Results.*
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.*
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class StrideAuthSpec extends SpecBase with MockitoSugar {

  /*
   * The type returned by:
   *
   * Retrievals.allEnrolments
   *   .and(Retrievals.email)
   *   .and(Retrievals.credentials)
   *   .and(Retrievals.name)
   *
   * Because Retrieval.and produces nested ~ values, the result type is:
   *
   * ~[
   *   ~[
   *     ~[
   *       Enrolments,
   *       Option[String]
   *     ],
   *     Option[Credentials]
   *   ],
   *   Option[Name]
   * ]
   */
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

  private val strideUser =
    TestStrideAuthUser.standardUser

  private def application =
    applicationBuilder().build()

  private def strideAuth(
      authConnector: AuthConnector,
      application: play.api.Application,
      config: Configuration = Configuration.from(
        Map(
          "appName"     -> "sdec",
          "stride.role" -> "SDEC_test_role"
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

  "StrideAuth" - {

    "must execute the supplied action when authentication succeeds" in {

      val authConnector =
        mock[AuthConnector]

      val app =
        application

      running(app) {

        val auth =
          strideAuth(
            authConnector,
            app
          )

        when(
          authConnector.authorise(
            any[Predicate],
            any[Retrieval[StrideRetrieval]]
          )(using
            any[HeaderCarrier],
            any[scala.concurrent.ExecutionContext]
          )
        ).thenReturn(
          Future.successful(successfulRetrieval)
        )

        val request =
          FakeRequest(GET, "/test")

        val result =
          auth
            .authorisedFromStride { (user, _) =>
              Future.successful(
                Ok(user.email)
              )
            }
            .apply(request)

        status(result) mustEqual OK
        contentAsString(result) mustEqual strideUser.email
      }
    }

    "must redirect to STRIDE login when there is no active session" in {

      val authConnector =
        mock[AuthConnector]

      val app =
        application

      running(app) {

        val config =
          Configuration.from(
            Map(
              "appName"                                     -> "sdec",
              "stride.role"                                 -> "SDEC_test_role",
              "Test.external-url.stride-auth-frontend.host" ->
                "http://localhost:9041"
            )
          )

        val auth =
          strideAuth(
            authConnector,
            app,
            config
          )

        when(
          authConnector.authorise(
            any[Predicate],
            any[Retrieval[StrideRetrieval]]
          )(using
            any[HeaderCarrier],
            any[scala.concurrent.ExecutionContext]
          )
        ).thenReturn(
          Future.failed(
            new NoActiveSession("No active session") {}
          )
        )

        val request =
          FakeRequest(
            GET,
            "/test"
          )

        val result =
          auth
            .authorisedFromStride { (_, _) =>
              Future.successful(
                Ok("should not be called")
              )
            }
            .apply(request)

        status(result) mustEqual SEE_OTHER

        redirectLocation(
          result
        ).get mustBe "http://localhost:9041/stride/sign-in?successURL=http%3A%2F%2Flocalhost%2Ftest&origin=sdec"
      }
    }

    "must redirect to the insufficient roles page when the user does not have the required enrolment" in {

      val authConnector =
        mock[AuthConnector]

      val app =
        application

      running(app) {

        val auth =
          strideAuth(
            authConnector,
            app
          )

        when(
          authConnector.authorise(
            any[Predicate],
            any[Retrieval[StrideRetrieval]]
          )(using
            any[HeaderCarrier],
            any[scala.concurrent.ExecutionContext]
          )
        ).thenReturn(
          Future.failed(
            new InsufficientEnrolments("Insufficient enrolments")
          )
        )

        val request =
          FakeRequest(
            GET,
            "/test"
          )

        val result =
          auth
            .authorisedFromStride { (_, _) =>
              Future.successful(
                Ok("should not be called")
              )
            }
            .apply(request)

        status(result) mustEqual SEE_OTHER

        redirectLocation(result) mustEqual Some(
          controllers.routes.InsufficientRolesController.get.url
        )
      }
    }

    "must not execute the supplied action when there is no active session" in {

      val authConnector =
        mock[AuthConnector]

      val app =
        application

      running(app) {

        val auth =
          strideAuth(
            authConnector,
            app
          )

        when(
          authConnector.authorise(
            any[Predicate],
            any[Retrieval[StrideRetrieval]]
          )(using
            any[HeaderCarrier],
            any[scala.concurrent.ExecutionContext]
          )
        ).thenReturn(
          Future.failed(
            new NoActiveSession("No active session") {}
          )
        )

        val action =
          mock[
            (
                models.StrideAuthUser,
                play.api.mvc.Request[play.api.mvc.AnyContent]
            ) => Future[play.api.mvc.Result]
          ]

        val request =
          FakeRequest(
            GET,
            "/test"
          )

        auth
          .authorisedFromStride { (user, request) =>
            action(user, request)
          }
          .apply(request)
          .futureValue

        verifyNoInteractions(action)
      }
    }

    "must not execute the supplied action when the user has insufficient enrolments" in {

      val authConnector =
        mock[AuthConnector]

      val app =
        application

      running(app) {

        val auth =
          strideAuth(
            authConnector,
            app
          )

        when(
          authConnector.authorise(
            any[Predicate],
            any[Retrieval[StrideRetrieval]]
          )(using
            any[HeaderCarrier],
            any[scala.concurrent.ExecutionContext]
          )
        ).thenReturn(
          Future.failed(
            new InsufficientEnrolments("Insufficient enrolments")
          )
        )

        val action =
          mock[
            (
                models.StrideAuthUser,
                play.api.mvc.Request[play.api.mvc.AnyContent]
            ) => Future[play.api.mvc.Result]
          ]

        val request =
          FakeRequest(
            GET,
            "/test"
          )

        auth
          .authorisedFromStride { (user, request) =>
            action(user, request)
          }
          .apply(request)
          .futureValue

        verifyNoInteractions(action)
      }
    }
  }
}
