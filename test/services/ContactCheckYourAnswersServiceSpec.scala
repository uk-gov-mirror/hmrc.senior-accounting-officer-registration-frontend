/*
 * Copyright 2025 HM Revenue & Customs
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
import models.*
import models.ContactHaveYouAddedAll.{No, Yes}
import models.ContactType.First
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import pages.*
import services.ContactCheckYourAnswersServiceSpec.*

class ContactCheckYourAnswersServiceSpec extends SpecBase with GuiceOneAppPerSuite {
  val SUT: ContactCheckYourAnswersService = app.injector.instanceOf[ContactCheckYourAnswersService]

  "ContactCheckYourAnswersService.getContactInfo" - {

    ContactType.values.foreach(contactType => {

      s"when getContactInfo (userAnswers, $contactType) is called" - {

        "when userAnswer has all contact info then return ContactInfo" in {

          val userAnswers = emptyUserAnswers
            .updateContact(contactType, "name", "email")

          val result = SUT.getContactInfo(userAnswers, contactType)

          result mustBe Some(ContactInfo("name", "email"))
        }

        "when userAnswer has no name then return None" in {
          val userAnswers = emptyUserAnswers
            .updateContact(contactType, None, Some("email"))
          val result = SUT.getContactInfo(userAnswers, contactType)
          result mustBe None
        }

        "when userAnswer has no email then return None" in {
          val userAnswers = emptyUserAnswers
            .updateContact(contactType, Some("name"), None)
          val result = SUT.getContactInfo(userAnswers, contactType)
          result mustBe None
        }
      }
    })
  }

  "ContactCheckYourAnswersService.getContacts" - {

    "when userAnswer has both contact info" - {
      "and the user answered there are two contacts must return both contacts" in {
        val userAnswers = emptyUserAnswers
          .updateContact(ContactType.First, "name1", "email1")
          .updateContactHaveYouAddedAll(No)
          .updateContact(ContactType.Second, "name2", "email2")

        val result = SUT.getContacts(userAnswers)

        result mustBe List(
          ContactInfo("name1", "email1"),
          ContactInfo("name2", "email2")
        )
      }

      "but the user answered there is only one contact must return only the first contact" in {
        val userAnswers = emptyUserAnswers
          .updateContact(ContactType.First, "name1", "email1")
          .updateContactHaveYouAddedAll(Yes)
          .updateContact(ContactType.Second, "name2", "email2")

        val result = SUT.getContacts(userAnswers)

        result mustBe List(
          ContactInfo("name1", "email1")
        )
      }
    }

    "when userAnswer only has one contact info" - {
      "must return the contact" in {
        val userAnswers = emptyUserAnswers
          .updateContact(ContactType.First, "name1", "email1")
          .updateContactHaveYouAddedAll(Yes)

        val result = SUT.getContacts(userAnswers)

        result mustBe List(
          ContactInfo("name1", "email1")
        )
      }
    }
  }

  "ContactCheckYourAnswersService.getContactsForCheckYourAnswers" - {
    "must return one contact when add another is yes" in {
      val userAnswers = emptyUserAnswers
        .updateContact(ContactType.First, "name1", "email1")
        .updateContactHaveYouAddedAll(Yes)
        .updateContact(ContactType.Second, "name2", "email2")

      SUT.getContactsForCheckYourAnswers(userAnswers) mustBe Some(
        ContactsCheckYourAnswers(
          firstContact = ContactInfo("name1", "email1"),
          secondContact = None,
          contactHaveYouAddedAll = Yes
        )
      )
    }

    "must return two contacts when add another is no and both second-contact answers exist" in {
      val userAnswers = emptyUserAnswers
        .updateContact(ContactType.First, "name1", "email1")
        .updateContactHaveYouAddedAll(No)
        .updateContact(ContactType.Second, "name2", "email2")

      SUT.getContactsForCheckYourAnswers(userAnswers) mustBe Some(
        ContactsCheckYourAnswers(
          firstContact = ContactInfo("name1", "email1"),
          secondContact = Some(ContactInfo("name2", "email2")),
          contactHaveYouAddedAll = No
        )
      )
    }

    "must return none when add another is no and second-contact answers are incomplete" in {
      val userAnswers = emptyUserAnswers
        .updateContact(ContactType.First, "name1", "email1")
        .updateContactHaveYouAddedAll(No)
        .updateContact(ContactType.Second, Some("name2"), None)

      SUT.getContactsForCheckYourAnswers(userAnswers) mustBe None
    }
  }

}

object ContactCheckYourAnswersServiceSpec {
  extension (userAnswers: UserAnswers) {
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
