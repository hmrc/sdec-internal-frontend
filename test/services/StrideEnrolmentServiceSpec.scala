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

import config.FrontendAppConfig
import models.StrideAuthUser
import org.mockito.Mockito.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name}
import uk.gov.hmrc.auth.core.{Enrolment, Enrolments}

class StrideEnrolmentServiceSpec
    extends AnyFlatSpec
    with Matchers
    with ScalaFutures {
  private val config = mock(classOf[FrontendAppConfig])
  when(config.strideRole).thenReturn("sdec_integration_tester")
  when(config.sdecAccessPrefixMatch).thenReturn("SDEC")

  private val sdecEnrolment =
    Enrolment("sdec_integration_tester", List(), "activated", None)
  private val anotherEnrolment =
    Enrolment("repay_caseworker", List(), "activated", None)

  Enrolments(Set(sdecEnrolment))

  private val striderUser = StrideAuthUser(
    credentialOptions = Some(Credentials("12345", "PrivilegedApplication")),
    email = Some("test@hmrc.gov.uk"),
    enrolments = Enrolments(Set(sdecEnrolment, anotherEnrolment)),
    nameOption = Some(Name(Some("Test User"), None))
  )
  val sut = new StrideEnrolmentService(config)

  it should "extract sdec enrolments" in {
    val result = sut.extractSdecEnrolments(striderUser.enrolments)

    result.enrolments.size shouldBe 1
    result.enrolments.head shouldBe sdecEnrolment
  }

  it should "verify STRIDE user with enrolment" in {
    val result = sut.verifyEnrollment(striderUser)
    whenReady(result) { answer =>
      answer shouldBe true
    }
  }

  it should "not verify STRIDE user for SDEC without enrolment" in {
    val unenrolled = striderUser.copy(enrolments = Enrolments(Set(anotherEnrolment)))

    val result = sut.verifyEnrollment(unenrolled)
    whenReady(result) { answer =>
      answer shouldBe false
    }
  }

}
