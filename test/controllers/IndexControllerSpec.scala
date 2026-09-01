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

package controllers

import base.SpecBase
import models.TestStrideAuthUser
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import strideauth.{StrideAuthAlgebra, TestStrideAuth}

class IndexControllerSpec extends SpecBase {

  "Index Controller" - {

    "must return OK for a GET when the user is authenticated with STRIDE" in {

      val application =
        applicationBuilder()
          .overrides(
            bind[StrideAuthAlgebra].to[TestStrideAuth],
            bind[models.StrideAuthUser]
              .toInstance(TestStrideAuthUser.standardUser)
          )
          .build()

      running(application) {

        val request =
          FakeRequest(
            GET,
            routes.IndexController.onPageLoad().url
          )

        val result =
          route(application, request).value

        status(result) mustEqual OK
      }
    }
  }
}
