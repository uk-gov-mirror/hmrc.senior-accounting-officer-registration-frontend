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

import config.AppConfig
import controllers.actions.*
import forms.ContactHaveYouAddedAllFormProvider
import models.requests.DataRequest
import models.{ContactHaveYouAddedAll, ContactType, Mode}
import navigation.Navigator
import pages.ContactHaveYouAddedAllPage
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.{ContactHaveYouAddedAllLegacyView, ContactHaveYouAddedAllView}

import scala.concurrent.{ExecutionContext, Future}

import javax.inject.Inject

class ContactHaveYouAddedAllController @Inject() (
    override val messagesApi: MessagesApi,
    sessionRepository: SessionRepository,
    navigator: Navigator,
    identify: IdentifierAction,
    getData: DataRetrievalAction,
    appConfig: AppConfig,
    requireData: DataRequiredAction,
    formProvider: ContactHaveYouAddedAllFormProvider,
    val controllerComponents: MessagesControllerComponents,
    view: ContactHaveYouAddedAllView,
    legacyView: ContactHaveYouAddedAllLegacyView
)(using ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  def form: Form[ContactHaveYouAddedAll] =
    formProvider(
      if appConfig.contactFlowReshuffleEnabled then "contactHaveYouAddedAll.error.required"
      else "contactHaveYouAddedAll.legacy.error.required"
    )

  private def render(form: Form[ContactHaveYouAddedAll], contactType: ContactType, mode: Mode)(using DataRequest[?]) =
    if appConfig.contactFlowReshuffleEnabled then view(form, contactType, mode)
    else legacyView(form, contactType, mode)

  def onPageLoad(contactType: ContactType, mode: Mode): Action[AnyContent] = {
    (identify andThen getData andThen requireData) { implicit request =>
      val preparedForm = request.userAnswers.get(ContactHaveYouAddedAllPage(contactType)) match {
        case None        => form
        case Some(value) => form.fill(value)
      }

      Ok(render(preparedForm, contactType, mode))
    }
  }

  def onPageLoadReshuffled(mode: Mode): Action[AnyContent] =
    (identify andThen getData andThen requireData) { implicit request =>
      val preparedForm = request.userAnswers.get(ContactHaveYouAddedAllPage(ContactType.First)) match {
        case None        => form
        case Some(value) => form.fill(value)
      }

      Ok(render(preparedForm, ContactType.First, mode))
    }

  def onSubmit(contactType: ContactType, mode: Mode): Action[AnyContent] =
    (identify andThen getData andThen requireData).async { implicit request =>
      form
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest(render(formWithErrors, contactType, mode))),
          value =>
            for {
              updatedAnswers <- Future.fromTry(request.userAnswers.set(ContactHaveYouAddedAllPage(contactType), value))
              _              <- sessionRepository.set(updatedAnswers)
            } yield Redirect(navigator.nextPage(ContactHaveYouAddedAllPage(contactType), mode, updatedAnswers))
        )
    }
}
