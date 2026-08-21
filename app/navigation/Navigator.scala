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

package navigation

import controllers.routes
import models.*
import models.ContactType.*
import pages.*
import play.api.mvc.Call

import javax.inject.{Inject, Singleton}

@Singleton
class Navigator @Inject() () {

  private val normalRoutes: Page => UserAnswers => Call = {
    case NominatedCompanyDetailsGuidancePage => _ => routes.GrsController.start()
    case ContactNamePage(contactType)        => _ => routes.ContactEmailController.onPageLoad(contactType, NormalMode)
    case ContactEmailPage(First)             => _ => routes.ContactHaveYouAddedAllController.onPageLoad(First, NormalMode)
    case ContactEmailPage(Second)            => _ => routes.ContactCheckYourAnswersController.onPageLoad()
    case ContactCheckYourAnswersPage         => _ => routes.IndexController.onPageLoad()
    case ContactHaveYouAddedAllPage(First) =>
      userAnswers =>
        if userAnswers.get(ContactHaveYouAddedAllPage(First)).contains(ContactHaveYouAddedAll.Yes) then {
          routes.ContactCheckYourAnswersController.onPageLoad()
        } else {
          routes.ContactNameController.onPageLoad(
            Second,
            NormalMode
          )
        }
    case _ =>
      _ => {
        routes.IndexController.onPageLoad()
      }
  }

  private val checkRouteMap: Page => UserAnswers => Call = {
    case ContactNamePage(contactType) =>
      _ => routes.ContactCheckYourAnswersController.onPageLoad()
    case ContactEmailPage(contactType) =>
      _ => routes.ContactCheckYourAnswersController.onPageLoad()
    case ContactHaveYouAddedAllPage(First) =>
      userAnswers =>
        userAnswers.get(ContactHaveYouAddedAllPage(First)) match {
          case Some(ContactHaveYouAddedAll.Yes) => routes.ContactCheckYourAnswersController.onPageLoad()
          case Some(ContactHaveYouAddedAll.No) if userAnswers.get(ContactNamePage(Second)).isDefined &&
              userAnswers.get(ContactEmailPage(Second)).isDefined =>
            routes.ContactCheckYourAnswersController.onPageLoad()
          case Some(ContactHaveYouAddedAll.No) =>
            routes.ContactNameController.onPageLoad(Second, NormalMode)
          case _ =>
            routes.IndexController.onPageLoad()
        }
    case _ => _ => routes.IndexController.onPageLoad()
  }

  def nextPage(page: Page, mode: Mode, userAnswers: UserAnswers): Call = mode match {
    case NormalMode =>
      normalRoutes(page)(userAnswers)
    case CheckMode =>
      checkRouteMap(page)(userAnswers)
  }
}
