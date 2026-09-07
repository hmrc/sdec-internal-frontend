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

import models.StrideAuthUser
import uk.gov.hmrc.auth.core.{Enrolment, Enrolments}

import scala.concurrent.Future

// The set of SDEC enrollments should come from LDAP/DB
class StrideEnrolmentService(sdecEnrollments: Enrolments)
    extends StrideEnrolmentServiceAlgebra {
  private val sdecMatch = "SDEC"

  override def extractSdecEnrolments(enrolments: Enrolments): Enrolments = {
    val filtered =
      enrolments.enrolments.filter(enrol =>
        enrol.key.toUpperCase.startsWith(sdecMatch)
      )
    Enrolments(filtered)
  }

  // This returns a future as, in implementation, the information will check against LDAP/DB
  override def verifyEnrollment(strideUser: StrideAuthUser): Future[Boolean] = {
    Future.successful(
      strideUser.enrolments.enrolments.exists(enrol =>
        sdecEnrollments.enrolments.exists(_.key.equalsIgnoreCase(enrol.key))
      )
    )
  }
}

object StrideEnrolmentService {
  val sdecEnrollments: Enrolments = Enrolments(
    Set(Enrolment("sdec_integration_tester"))
  )

  def apply(): StrideEnrolmentServiceAlgebra = new StrideEnrolmentService(
    sdecEnrollments
  )
}
