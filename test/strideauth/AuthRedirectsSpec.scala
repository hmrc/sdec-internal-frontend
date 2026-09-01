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

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.http.HeaderNames.LOCATION
import play.api.http.Status.SEE_OTHER
import play.api.{Configuration, Environment, Mode}

class AuthRedirectsSpec extends AnyFreeSpec with Matchers {

  private def testAuthRedirects(
      configuration: Configuration
  ): AuthRedirects =
    new AuthRedirects {
      override val config: Configuration = configuration
      override val env: Environment      =
        Environment.simple(
          mode = Mode.Test
        )
    }

  "AuthRedirects" - {

    "must return the configured STRIDE login URL in Test mode" in {

      val configuration =
        Configuration.from(
          Map(
            "appName"                                     -> "sdec",
            "Test.external-url.stride-auth-frontend.host" ->
              "http://localhost:9041"
          )
        )

      val redirects =
        testAuthRedirects(configuration)

      redirects.strideLoginUrl mustEqual
        "http://localhost:9041/stride/sign-in"
    }

    "must redirect to STRIDE login with the success URL and origin" in {

      val configuration =
        Configuration.from(
          Map(
            "appName"                                     -> "sdec",
            "Test.external-url.stride-auth-frontend.host" ->
              "http://localhost:9041"
          )
        )

      val redirects =
        testAuthRedirects(configuration)

      val result =
        redirects.toStrideLogin(
          successUrl = "/sdec/test"
        )

      result.header.status mustEqual SEE_OTHER

      result.header.headers.get(LOCATION) mustEqual Some(
        "http://localhost:9041/stride/sign-in" +
          "?successURL=%2Fsdec%2Ftest&origin=sdec"
      )
    }

    "must include the failure URL when one is supplied" in {

      val configuration =
        Configuration.from(
          Map(
            "appName"                                     -> "sdec",
            "Test.external-url.stride-auth-frontend.host" ->
              "http://localhost:9041"
          )
        )

      val redirects =
        testAuthRedirects(configuration)

      val result =
        redirects.toStrideLogin(
          successUrl = "/sdec/test",
          failureUrl = Some("/sdec/error")
        )

      result.header.status mustEqual SEE_OTHER

      result.header.headers.get(LOCATION) mustEqual Some(
        "http://localhost:9041/stride/sign-in" +
          "?successURL=%2Fsdec%2Ftest" +
          "&origin=sdec" +
          "&failureURL=%2Fsdec%2Ferror"
      )
    }

    "must not include a failure URL when one is not supplied" in {

      val configuration =
        Configuration.from(
          Map(
            "appName"                                     -> "sdec",
            "Test.external-url.stride-auth-frontend.host" ->
              "http://localhost:9041"
          )
        )

      val redirects =
        testAuthRedirects(configuration)

      val result =
        redirects.toStrideLogin(
          successUrl = "/sdec/test"
        )

      val location =
        result.header.headers.getOrElse(LOCATION, "")

      location must not include "failureURL"
    }

    "must use the Test host rather than the Dev host when running in Test mode" in {

      val configuration =
        Configuration.from(
          Map(
            "appName"                                    -> "sdec",
            "run.mode"                                   -> "Dev",
            "Dev.external-url.stride-auth-frontend.host" ->
              "http://dev-host:9041",
            "Test.external-url.stride-auth-frontend.host" ->
              "http://test-host:9041"
          )
        )

      val redirects =
        testAuthRedirects(configuration)

      redirects.strideLoginUrl mustEqual
        "http://test-host:9041/stride/sign-in"
    }

    "must use the Dev host when not running in Test mode and run.mode is Dev" in {

      val configuration =
        Configuration.from(
          Map(
            "appName"                                    -> "sdec",
            "run.mode"                                   -> "Dev",
            "Dev.external-url.stride-auth-frontend.host" ->
              "http://dev-host:9041"
          )
        )

      val redirects =
        new AuthRedirects {
          override val config: Configuration = configuration
          override val env: Environment      =
            Environment.simple(
              mode = Mode.Prod
            )
        }

      redirects.strideLoginUrl mustEqual
        "http://dev-host:9041/stride/sign-in"
    }

    "must fall back to the default Dev STRIDE host when no Dev host is configured" in {

      val configuration =
        Configuration.from(
          Map(
            "appName"  -> "sdec",
            "run.mode" -> "Dev"
          )
        )

      val redirects =
        new AuthRedirects {
          override val config: Configuration = configuration
          override val env: Environment      =
            Environment.simple(
              mode = Mode.Prod
            )
        }

      redirects.strideLoginUrl mustEqual
        "http://localhost:9041/stride/sign-in"
    }

    "must use only the path when no Test STRIDE host is configured" in {

      val configuration =
        Configuration.from(
          Map(
            "appName" -> "sdec"
          )
        )

      val redirects =
        testAuthRedirects(configuration)

      redirects.strideLoginUrl mustEqual
        "/stride/sign-in"
    }

    "must preserve the success URL when it contains query parameters" in {

      val configuration =
        Configuration.from(
          Map(
            "appName"                                     -> "sdec",
            "Test.external-url.stride-auth-frontend.host" ->
              "http://localhost:9041"
          )
        )

      val redirects =
        testAuthRedirects(configuration)

      val result =
        redirects.toStrideLogin(
          successUrl = "/sdec/page?foo=bar"
        )

      result.header.status mustEqual SEE_OTHER

      val location =
        result.header.headers.getOrElse(LOCATION, "")

      location must include(
        "successURL=%2Fsdec%2Fpage%3Ffoo%3Dbar"
      )
    }

    "must include success URL, origin and failure URL together" in {

      val configuration =
        Configuration.from(
          Map(
            "appName"                                     -> "sdec",
            "Test.external-url.stride-auth-frontend.host" ->
              "http://localhost:9041"
          )
        )

      val redirects =
        testAuthRedirects(configuration)

      val result =
        redirects.toStrideLogin(
          successUrl = "/sdec/success",
          failureUrl = Some("/sdec/failure")
        )

      result.header.status mustEqual SEE_OTHER

      val location =
        result.header.headers.getOrElse(LOCATION, "")

      location must include(
        "successURL=%2Fsdec%2Fsuccess"
      )

      location must include(
        "origin=sdec"
      )

      location must include(
        "failureURL=%2Fsdec%2Ffailure"
      )
    }
  }
}
