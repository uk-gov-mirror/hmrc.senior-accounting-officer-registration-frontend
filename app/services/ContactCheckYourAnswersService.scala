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

import models.*
import models.ContactType.{First, Second}
import pages.*

class ContactCheckYourAnswersService {

  def getContactInfo(userAnswers: UserAnswers, contactType: ContactType): Option[ContactInfo] =
    for {
      name  <- userAnswers.get(ContactNamePage(contactType))
      email <- userAnswers.get(ContactEmailPage(contactType))
    } yield ContactInfo(name, email)

  def getContactsForCheckYourAnswers(userAnswers: UserAnswers): Option[ContactsCheckYourAnswers] =
    for {
      firstContact        <- getContactInfo(userAnswers, First)
      contactHaveAddedAll <- userAnswers.get(ContactHaveYouAddedAllPage(First))
      secondContact = contactHaveAddedAll match {
        case ContactHaveYouAddedAll.Yes => None
        case ContactHaveYouAddedAll.No  => getContactInfo(userAnswers, Second)
      }
      if contactHaveAddedAll != ContactHaveYouAddedAll.No || secondContact.isDefined
    } yield ContactsCheckYourAnswers(firstContact, secondContact, contactHaveAddedAll)

  def getContacts(userAnswers: UserAnswers): List[ContactInfo] =
    List(
      getContactInfo(userAnswers = userAnswers, contactType = First),
      getContactInfo(userAnswers = userAnswers, contactType = Second).filter { _ =>
        userAnswers.get(ContactHaveYouAddedAllPage(First)).contains(ContactHaveYouAddedAll.No)
      }
    ).flatten
}
