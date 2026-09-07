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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration

class AuthRedirectsSpec extends AnyWordSpec with Matchers {

  "AuthRedirects" should {

    "read the STRIDE login URL from configuration" in {

      val configuration = Configuration.from(
        Map(
          "urls.strideLogin" -> "http://localhost:9041/stride/sign-in",
          "appName"          -> "sdec-internal-frontend"
        )
      )

      val authRedirects = new AuthRedirects {
        override def config: Configuration = configuration
      }

      authRedirects.strideLoginUrl shouldBe
        "http://localhost:9041/stride/sign-in"
    }

    "read the application name from configuration" in {

      val configuration = Configuration.from(
        Map(
          "urls.strideLogin" -> "http://localhost:9041/stride/sign-in",
          "appName"          -> "sdec-internal-frontend"
        )
      )

      val authRedirects = new AuthRedirects {
        override def config: Configuration = configuration
      }

      authRedirects.origin shouldBe
        "sdec-internal-frontend"
    }

    "create a redirect to the configured STRIDE login URL" in {

      val configuration = Configuration.from(
        Map(
          "urls.strideLogin" -> "http://localhost:9041/stride/sign-in",
          "appName"          -> "sdec-internal-frontend"
        )
      )

      val authRedirects = new AuthRedirects {
        override def config: Configuration = configuration
      }

      val result = authRedirects.toStrideLogin(
        successUrl = "http://localhost:4000/sdec-internal-frontend"
      )

      result.header.status shouldBe 303

      val location = result.header.headers("Location")

      location should startWith(
        "http://localhost:9041/stride/sign-in?"
      )

      location should include("successURL=")

      location should include(
        "origin=sdec-internal-frontend"
      )

      location should not include "failureURL="
    }

    "include the failure URL when one is supplied" in {

      val configuration = Configuration.from(
        Map(
          "urls.strideLogin" -> "http://localhost:9041/stride/sign-in",
          "appName"          -> "sdec-internal-frontend"
        )
      )

      val authRedirects = new AuthRedirects {
        override def config: Configuration = configuration
      }

      val result = authRedirects.toStrideLogin(
        successUrl = "http://localhost:4000/sdec-internal-frontend",
        failureUrl = Some("http://localhost:4000/sdec-internal-frontend/failure")
      )

      result.header.status shouldBe 303

      val location = result.header.headers("Location")

      location should startWith(
        "http://localhost:9041/stride/sign-in?"
      )

      location should include("successURL=")

      location should include(
        "origin=sdec-internal-frontend"
      )

      location should include("failureURL=")
    }
  }
}
