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

import base.SpecBase
import config.FeatureToggleSupport
import connectors.SignUpConnector
import models.ContactHaveYouAddedAll.{No, Yes}
import models.ContactType.*
import models.config.FeatureToggle.ContactFlowReshuffle
import models.registration.*
import models.{ContactHaveYouAddedAll, ContactType, UserAnswers}
import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import pages.*
import play.api.inject.*
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, inject}
import services.SignUpService.SignUpResult.*
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.Future

import SignUpServiceSpec.*

class SignUpServiceSpec
    extends SpecBase
    with GuiceOneAppPerSuite
    with MockitoSugar
    with BeforeAndAfterEach
    with FeatureToggleSupport {

  val mockSignUpConnector: SignUpConnector = mock[SignUpConnector]

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .overrides(bind[SignUpConnector].toInstance(mockSignUpConnector))
    .build()

  override def beforeEach(): Unit = {
    disable(ContactFlowReshuffle)
    reset(mockSignUpConnector)
  }

  override def afterEach(): Unit = disable(ContactFlowReshuffle)

  Seq(false, true).foreach { reshuffled =>
    Seq(Yes, No).foreach { answer =>
      s"submission with reshuffle=$reshuffled and answer=$answer must select the correct contacts" in {
        if reshuffled then enable(ContactFlowReshuffle)
        when(mockSignUpConnector.submit(any())(using any()))
          .thenReturn(Future.successful(HttpResponse(200, """{"subscriptionId":"id"}""")))
        val answers = emptyUserAnswers
          .updateGrs(testCompanyDetails)
          .updateContact(First, "name1", "email1")
          .updateContactHaveYouAddedAll(answer)
          .updateContact(Second, "name2", "email2")
        val wantsSecond = if reshuffled then answer == Yes else answer == No
        val contacts    = List(Contact("name1", "email1", "valid", "en-GB")) ++
          (if wantsSecond then List(Contact("name2", "email2", "valid", "en-GB")) else Nil)

        SUT.submit(answers).futureValue mustBe Success("id")
        verify(mockSignUpConnector).submit(
          meq(
            SignUpRequest(
              etmpSafeId = "registeredBusinessPartnerId",
              nominatedCompany = NominatedCompany(name = "companyName", crn = "companyNumber", utr = "ctUtr"),
              contacts = contacts
            )
          )
        )(using any())
      }
    }
  }

  Seq((None, None), (Some("name2"), None), (None, Some("email2"))).foreach { (name, email) =>
    s"reshuffled submission must reject incomplete second contact ($name, $email) when yes is selected" in {
      enable(ContactFlowReshuffle)
      val answers = emptyUserAnswers
        .updateGrs(testCompanyDetails)
        .updateContact(First, "name1", "email1")
        .updateContactHaveYouAddedAll(Yes)
        .updateContact(Second, name, email)
      SUT.submit(answers).futureValue mustBe InsufficientUserAnswers
      verifyNoInteractions(mockSignUpConnector)
    }
  }

  def SUT: SignUpService = app.injector.instanceOf[SignUpService]

  given HeaderCarrier = HeaderCarrier()

  "SignUpService.getContactInfo" - {
    "when user answer is valid and complete and calling the sign up end point returned a valid 200 response" - {
      "must return Success(subscriptionId) when user answer has the company details" - {
        "and exactly one contact detail" in {
          val testSubscriptionId = "id"
          when(mockSignUpConnector.submit(any())(using any())).thenReturn(
            Future.successful(
              HttpResponse(
                200,
                s"""{"subscriptionId": "$testSubscriptionId"}"""
              )
            )
          )
          val userAnswers = emptyUserAnswers
            .updateGrs(testCompanyDetails)
            .updateContact(First, "name", "email")
            .updateContactHaveYouAddedAll(Yes)

          val result = SUT.submit(userAnswers)

          result.futureValue mustBe Success(testSubscriptionId)

          verify(mockSignUpConnector, times(1)).submit(
            meq(
              SignUpRequest(
                etmpSafeId = "registeredBusinessPartnerId",
                nominatedCompany = NominatedCompany(
                  name = "companyName",
                  utr = "ctUtr",
                  crn = "companyNumber"
                ),
                contacts = List(Contact("name", "email", "valid", "en-GB"))
              )
            )
          )(using any())
        }
        "and have two contact details" in {
          val testSubscriptionId = "id"
          when(mockSignUpConnector.submit(any())(using any()))
            .thenReturn(
              Future.successful(HttpResponse(200, s"""{"subscriptionId": "$testSubscriptionId"}"""))
            )
          val userAnswers = emptyUserAnswers
            .updateGrs(testCompanyDetails)
            .updateContact(First, "name1", "email1")
            .updateContactHaveYouAddedAll(No)
            .updateContact(Second, "name2", "email2")

          val result = SUT.submit(userAnswers)

          result.futureValue mustBe Success(testSubscriptionId)

          verify(mockSignUpConnector, times(1)).submit(
            meq(
              SignUpRequest(
                etmpSafeId = "registeredBusinessPartnerId",
                nominatedCompany = NominatedCompany(
                  name = "companyName",
                  utr = "ctUtr",
                  crn = "companyNumber"
                ),
                contacts =
                  List(Contact("name1", "email1", "valid", "en-GB"), Contact("name2", "email2", "valid", "en-GB"))
              )
            )
          )(using any())
        }
      }

      "must return InsufficientUserAnswers when user answer is incomplete" - {
        "when there is nothing in user answers" in {
          val result = SUT.submit(emptyUserAnswers)

          result.futureValue mustBe InsufficientUserAnswers
          verify(mockSignUpConnector, times(0)).submit(any())(using any())
        }
        "when there is only Company Details in user answers" in {
          val result = SUT.submit(emptyUserAnswers.updateGrs(testCompanyDetails))

          result.futureValue mustBe InsufficientUserAnswers
          verify(mockSignUpConnector, times(0)).submit(any())(using any())
        }
        "when there is only Contact Details in user answers" in {
          val result = SUT.submit(
            emptyUserAnswers
              .updateContact(First, "name1", "email1")
              .updateContactHaveYouAddedAll(No)
              .updateContact(Second, "name2", "email2")
          )

          result.futureValue mustBe InsufficientUserAnswers
          verify(mockSignUpConnector, times(0)).submit(any())(using any())
        }
      }
    }

    "when user answer is valid and complete" - {
      "must return MalformedResponse when sign up end point returned an invalid 200 response" in {
        when(mockSignUpConnector.submit(any())(using any()))
          .thenReturn(Future.successful(HttpResponse(200, "{}")))
        val userAnswers = emptyUserAnswers
          .updateGrs(testCompanyDetails)
          .updateContact(First, "name1", "email1")
          .updateContactHaveYouAddedAll(Yes)

        val result = SUT.submit(userAnswers)

        result.futureValue mustBe MalformedResponse

        verify(mockSignUpConnector, times(1)).submit(any())(using any())
      }

      "must return BadRequestFailure when sign up end point returned a 400 response" in {
        when(mockSignUpConnector.submit(any())(using any()))
          .thenReturn(Future.successful(HttpResponse(400, "")))
        val userAnswers = emptyUserAnswers
          .updateGrs(testCompanyDetails)
          .updateContact(First, "name1", "email1")
          .updateContactHaveYouAddedAll(Yes)

        val result = SUT.submit(userAnswers)

        result.futureValue mustBe BadRequestFailure

        verify(mockSignUpConnector, times(1)).submit(any())(using any())
      }

      "must return ProtectedServiceFailure(500) when sign up end point returned a 500 response" in {
        when(mockSignUpConnector.submit(any())(using any()))
          .thenReturn(Future.successful(HttpResponse(500, "")))
        val userAnswers = emptyUserAnswers
          .updateGrs(testCompanyDetails)
          .updateContact(First, "name1", "email1")
          .updateContactHaveYouAddedAll(Yes)

        val result = SUT.submit(userAnswers)

        result.futureValue mustBe ProtectedServiceFailure(500)

        verify(mockSignUpConnector, times(1)).submit(any())(using any())
      }

      "must return ProtectedServiceFailure(502) when sign up end point returned a 502 response" in {
        when(mockSignUpConnector.submit(any())(using any()))
          .thenReturn(Future.successful(HttpResponse(502, "")))
        val userAnswers = emptyUserAnswers
          .updateGrs(testCompanyDetails)
          .updateContact(First, "name1", "email1")
          .updateContactHaveYouAddedAll(Yes)

        val result = SUT.submit(userAnswers)

        result.futureValue mustBe ProtectedServiceFailure(502)

        verify(mockSignUpConnector, times(1)).submit(any())(using any())
      }

      "must return UnknownFailure(status) when sign up end point returned an unknown status response" in {
        when(mockSignUpConnector.submit(any())(using any()))
          .thenReturn(Future.successful(HttpResponse(600, "")))
        val userAnswers = emptyUserAnswers
          .updateGrs(testCompanyDetails)
          .updateContact(First, "name1", "email1")
          .updateContactHaveYouAddedAll(Yes)

        val result = SUT.submit(userAnswers)

        result.futureValue mustBe UnknownFailure(600)

        verify(mockSignUpConnector, times(1)).submit(any())(using any())
      }
    }

  }
}

object SignUpServiceSpec {

  val testCompanyDetails: CompanyDetails = CompanyDetails(
    companyName = "companyName",
    companyNumber = "companyNumber",
    ctUtr = "ctUtr",
    registeredBusinessPartnerId = "registeredBusinessPartnerId"
  )

  extension (userAnswers: UserAnswers) {
    def updateGrs(companyDetails: CompanyDetails): UserAnswers =
      userAnswers.set(CompanyDetailsPage, companyDetails).get

    def updateContact(
        contactType: ContactType,
        name: String,
        email: String
    ): UserAnswers =
      updateContact(contactType, Some(name), Some(email))

    def updateContact(
        contactType: ContactType,
        name: Option[String],
        email: Option[String]
    ): UserAnswers =
      List(name, email).zipWithIndex
        .foldLeft(userAnswers)((accumulator, configs) => {
          configs match {
            case Some(value) -> 0 => accumulator.set(ContactNamePage(contactType), value).get
            case Some(value) -> 1 => accumulator.set(ContactEmailPage(contactType), value).get
            case _                => accumulator
          }
        })

    def updateContactHaveYouAddedAll(value: ContactHaveYouAddedAll): UserAnswers =
      userAnswers.set(ContactHaveYouAddedAllPage(First), value).get
  }

}
