/*
 * Copyright 2023 HM Revenue & Customs
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

import com.google.inject.Inject
import models.StrideAuthUser
import play.api.mvc.*
import play.api.{Configuration, Environment, Logging}
import services.StrideEnrolmentServiceAlgebra
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.AuthProvider.PrivilegedApplication
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendHeaderCarrierProvider

import scala.concurrent.{ExecutionContext, Future}

trait StrideAuthAlgebra {
  def authorisedFromStride(
      action: (StrideAuthUser, Request[AnyContent]) => Future[Result]
  )(implicit ec: ExecutionContext): Action[AnyContent]
}

class StrideAuth @Inject() (
    val authConnector: AuthConnector,
    val env: Environment,
    val config: Configuration,
    actionBuilder: DefaultActionBuilder,
    strideEnrolmentService: StrideEnrolmentServiceAlgebra
) extends StrideAuthAlgebra
    with AuthRedirects
    with AuthorisedFunctions
    with Results
    with FrontendHeaderCarrierProvider
    with Logging {

  val role: String             = config.get[String]("stride.role")
  val loginContinueUrl: String = config.get[String]("urls.loginContinue")

  override def authorisedFromStride(
      action: (StrideAuthUser, Request[AnyContent]) => Future[Result]
  )(implicit ec: ExecutionContext): Action[AnyContent] =
    actionBuilder.async { implicit request =>
      authorised(AuthProviders(PrivilegedApplication))
        .retrieve(
          Retrievals.allEnrolments
            .and(Retrievals.email)
            .and(Retrievals.credentials)
            .and(
              Retrievals.name
            ) // See https://confluence.tools.tax.service.gov.uk/spaces/SDEC/pages/1381269850/Extracting+External+Internal+User+Name
        ) { case allEnrolments ~ email ~ credentials ~ name =>

          val sdecEnrolments =
            strideEnrolmentService.extractSdecEnrolments(allEnrolments)

          val strideUser =
            StrideAuthUser(
              credentials,
              email,
              sdecEnrolments,
              name
            )

          strideEnrolmentService.verifyEnrollment(strideUser).flatMap {
            case true  => action(strideUser, request)
            case false =>
              Future.successful(
                SeeOther(
                  controllers.routes.InsufficientRolesController.get.url
                )
              )
          }
        }
        .recoverWith {

          case e: NoActiveSession =>
            logger.warn(s"No active session: ${e.reason}")

            Future.successful(
              toStrideLogin(
                loginContinueUrl
              )
            )

          case e: InsufficientEnrolments =>
            logger.warn(s"Insufficient enrollment: ${e.msg}")

            Future.successful(
              SeeOther(
                controllers.routes.InsufficientRolesController.get.url
              )
            )
        }
    }
}
