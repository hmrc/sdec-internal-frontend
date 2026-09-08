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

package services

import jakarta.inject.Singleton
import models.StrideAuthUser
import play.api.Logging
import play.api.mvc.*

import scala.concurrent.Future

@Singleton
class LogStrideUserService extends SdecServiceAlgebra[Unit] with Logging {

  override def process(
    strideUser: StrideAuthUser,
    request:    Request[AnyContent]
  ): Future[Unit] = {

    logger.info("===========================================================")
    logger.info(
      s"Enrollments: [${strideUser.enrolments.enrolments.mkString(",")}]"
    )
    logger.info("===========================================================")
    logger.info(s"Email: ${strideUser.email}")
    logger.info("===========================================================")
    logger.info(s"Credentials: [${strideUser.credentials}]")
    logger.info("===========================================================")
    request.headers.toMap.foreach { (k, v) =>
      logger.info(s"Header Information: Key: [$k] => Value: [${v.mkString(",")}]")
    }
    logger.info("===========================================================")
    logger.info(s"Name: ${strideUser.name}")
    logger.info("===========================================================")
    Future.successful(())
  }
}
