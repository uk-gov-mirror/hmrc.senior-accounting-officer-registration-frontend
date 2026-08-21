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

package views

import base.ViewSpecBase
import models.{ContactHaveYouAddedAll, ContactInfo, ContactsCheckYourAnswers}
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest.Assertion
import views.ContactCheckYourAnswersViewSpec.*
import views.html.ContactCheckYourAnswersView

import scala.jdk.CollectionConverters.*

class ContactCheckYourAnswersViewSpec extends ViewSpecBase[ContactCheckYourAnswersView] {

  "ContactCheckYourAnswersView" - {
    "must generate a view for one contact" in {
      val doc: Document = Jsoup.parse(SUT(oneContactAnswers).toString)

      doc.title() mustBe s"$pageTitle - Senior Accounting Officer notification and certificate - GOV.UK"
      doc.getMainContent.getElementsByTag("h1").text() mustBe pageHeading
      doc.getElementsByClass("govuk-back-link").size() mustBe 1
      doc.getMainContent.select("a.govuk-link.hmrc-report-technical-issue").text() mustBe
        "Is this page not working properly? (opens in new tab)"
      doc.select(".govuk-caption-l").text() mustBe pageTitle
      validateSummary(doc, expectedRows = List(
        ("Full name", "name1", "/senior-accounting-officer/registration/contact-details/first/change-name"),
        ("Email address", "email1", "/senior-accounting-officer/registration/contact-details/first/change-email"),
        ("Have you added all the contacts you need?", "Yes", "/senior-accounting-officer/registration/contact-details/first/change-add-another")
      ))

      val form = doc.select("form")
      form.attr("action") mustBe controllers.routes.ContactCheckYourAnswersController.saveAndContinue().url
      doc.getElementById("submit").text() mustBe submitButtonText
    }

    "must generate a view for two contacts" in {
      val doc: Document = Jsoup.parse(SUT(twoContactAnswers).toString)

      validateSummary(doc, expectedRows = List(
        ("Full name", "name1", "/senior-accounting-officer/registration/contact-details/first/change-name"),
        ("Email address", "email1", "/senior-accounting-officer/registration/contact-details/first/change-email"),
        ("Full name", "name2", "/senior-accounting-officer/registration/contact-details/second/change-name"),
        ("Email address", "email2", "/senior-accounting-officer/registration/contact-details/second/change-email"),
        ("Have you added all the contacts you need?", "No, add another contact", "/senior-accounting-officer/registration/contact-details/first/change-add-another")
      ))
    }
  }

  private def validateSummary(doc: Document, expectedRows: List[(String, String, String)]): Assertion = {
    val rows = doc.getMainContent.select("div.govuk-summary-list__row")
    rows.size() mustBe expectedRows.size

    rows.asScala.zipWithIndex.foreach { case (row, index) =>
      val (expectedKey, expectedValue, expectedHref) = expectedRows(index)

      row.select("dt.govuk-summary-list__key").text() mustBe expectedKey
      row.select("dd.govuk-summary-list__value").text() mustBe expectedValue
      row.select("dd.govuk-summary-list__actions a").attr("href") mustBe expectedHref
    }

    succeed
  }
}

object ContactCheckYourAnswersViewSpec {
  val pageTitle: String      = "Contact details"
  val pageHeading: String    = "Check your answers"
  val submitButtonText: String = "Continue"

  val oneContactAnswers: ContactsCheckYourAnswers = ContactsCheckYourAnswers(
    firstContact = ContactInfo("name1", "email1"),
    secondContact = None,
    contactHaveYouAddedAll = ContactHaveYouAddedAll.Yes
  )

  val twoContactAnswers: ContactsCheckYourAnswers = ContactsCheckYourAnswers(
    firstContact = ContactInfo("name1", "email1"),
    secondContact = Some(ContactInfo("name2", "email2")),
    contactHaveYouAddedAll = ContactHaveYouAddedAll.No
  )
}
