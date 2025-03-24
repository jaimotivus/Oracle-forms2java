<%@page import="org.springframework.security.web.csrf.CsrfToken"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ page isELIgnored="false"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<% session.invalidate(); %>

<div
	class="kt-grid kt-grid--hor kt-grid--root  
	kt-login kt-login--v3 kt-login--signin"
	id="kt_login">
	<div
		class="kt-grid__item 
		kt-grid__item--fluid kt-grid kt-grid--hor"
		style="background: #FFFFFF;">
		<div class="kt-grid__item kt-grid__item--fluid kt-login__wrapper">
			<div class="kt-login__container">
				<div class="kt-login__logo" style="margin-bottom: 20px;">
					<a href="javascript:;"> <img src="assets/media/logos/logo.png">
					</a>
				</div>
				<div ng-show="!isActivate">
					<div class="kt-login__signin">

						<form class="kt-form" method="post" id="loginForm" action="login">

							<div class="alert alert-danger fade show" role="alert"
								ng-show="isError">
								<div class="alert-icon">
									<i class="flaticon-close"></i>
								</div>
								<div class="alert-text">Usuario y/o contraseña inválidos.
								</div>
								<div class="alert-close">
									<button type="button" class="close" data-dismiss="alert"
										aria-label="Close">
										<span aria-hidden="true"> <i class="la la-close"></i>
										</span>
									</button>
								</div>
							</div>

							<div class="alert alert-danger fade show" role="alert"
								ng-show="isBlock">
								<div class="alert-icon">
									<i class="flaticon-close"></i>
								</div>
								<div class="alert-text">Lo sentimos, tu usuario ha sido
									bloqueado.</div>
								<div class="alert-close">
									<button type="button" class="close" data-dismiss="alert"
										aria-label="Close">
										<span aria-hidden="true"> <i class="la la-close"></i>
										</span>
									</button>
								</div>
							</div>

							<div class="alert alert-success fade show" role="alert"
								ng-show="isLogout">
								<div class="alert-icon">
									<i class="flaticon-logout"></i>
								</div>
								<div class="alert-text">Se ha cerrado correctamente tu
									sesión.</div>
								<div class="alert-close">
									<button type="button" class="close" data-dismiss="alert"
										aria-label="Close">
										<span aria-hidden="true"> <i class="la la-check"></i>
										</span>
									</button>
								</div>
							</div>
							
							<div class="alert alert-success fade show" role="alert"
								ng-show="isSessionTimeout">
								<div class="alert-icon">
									<i class="flaticon-logout"></i>
								</div>
								<div class="alert-text">Por seguridad, tu sesión se ha
									cerrado.</div>
								<div class="alert-close">
									<button type="button" class="close" data-dismiss="alert"
										aria-label="Close">
										<span aria-hidden="true"> <i class="la la-check"></i>
										</span>
									</button>
								</div>
							</div>

							<div class="alert alert-danger fade show" role="alert"
								ng-show="isActivationError">
								<div class="alert-icon">
									<i class="flaticon-close"></i>
								</div>
								<div class="alert-text">Lo sentimos, pero la información
									no es válida. Si el problema persiste, ejecuta el proceso de
									recuperación de contraseña.</div>
								<div class="alert-close">
									<button type="button" class="close" data-dismiss="alert"
										aria-label="Close">
										<span aria-hidden="true"> <i class="la la-close"></i>
										</span>
									</button>
								</div>
							</div>
							
							<div class="alert alert-danger fade show" role="alert"
								ng-show="isvalidationError">
								<div class="alert-icon">
									<i class="flaticon-close"></i>
								</div>
								<div class="alert-text">Lo sentimos, ocurrio un error en el proceso de login. Intentelo más tarde.</div>
								<div class="alert-close">
									<button type="button" class="close" data-dismiss="alert"
										aria-label="Close">
										<span aria-hidden="true"> <i class="la la-close"></i>
										</span>
									</button>
								</div>
							</div>

							<div class="alert alert-success fade show" role="alert"
								ng-show="isActivationSuccess">
								<div class="alert-icon">
									<i class="flaticon-confetti"></i>
								</div>
								<div class="alert-text">Hemos actualizado correctamente tu
									contraseña, ahora inicia sesión.</div>
								<div class="alert-close">
									<button type="button" class="close" data-dismiss="alert"
										aria-label="Close">
										<span aria-hidden="true"> <i class="la la-check"></i>
										</span>
									</button>
								</div>
							</div>
							
							<div class="alert alert-danger fade show" role="alert"
								ng-show="isexpiredPassword">
								<div class="alert-icon">
									<i class="flaticon-close"></i>
								</div>
								<div class="alert-text">Tu contraseña expiró y por seguridad debes cambiarla.</div>								
								<div class="alert-close">
									<button type="button" class="close" data-dismiss="alert"
										aria-label="Close">
										<span aria-hidden="true"> <i class="la la-check"></i>
										</span>
									</button>
								</div>
							</div>							
							<div class="row kt-login__extra" ng-show="isexpiredPassword">
								<div class="col kt-align-right">
									<a href="login#!/expiredPassword" id="kt_login_forgot"
										class="kt-login__link">Actualiza tu contraseña aquí.</a>
								</div>
							</div>

							<div class="form-group">
								<input class="form-control" type="text"
									placeholder="clave de usuario" name="email" autocomplete="off">
							</div>
							
							<div class="form-group">
								<input class="form-control" type="password"
									placeholder="contraseña" name="password" autocomplete="off">
							</div>

							<input type="hidden"
								name="<%=((CsrfToken) request.getAttribute("_csrf")).getParameterName()%>"
								value="<%=((CsrfToken) request.getAttribute("_csrf")).getToken()%>" />

							<div class="row kt-login__extra">
								<div class="col">
									<span class="kt-badge kt-badge--success kt-badge--inline">v2.59.0</span> 
								</div>
								<div class="col kt-align-right" ng-show="!isexpiredPassword">
									<a href="login#!/recoverPassword" id="kt_login_forgot"
										class="kt-login__link">¿Olvidaste tu contraseña?</a>
									<br>
									<a href="https://www.mitec.com.mx/avisodeprivacidad" id="kt_login_forgot"
										class="kt-login__link">Aviso de privacidad</a>
								</div>
							</div>
							<div class="kt-login__actions">
								<button id="loginBtn"
									type="submit"
									class="btn btn-brand btn-elevate kt-login__btn-primary">
									Entrar
								</button>
							</div>
						</form>
					</div>
				</div>
				<div ng-show="isActivate">
					<div class="kt-login__signin">

						<h3 class="font-size-24" style="text-align: center;">
							¡Bienvenido de nuevo!<br /><br />
						</h3>

						<div class="alert alert-info fade show" role="alert" ng-show="(isActivate || isActivationError) && !isActivationSuccess">
							<div class="alert-text">
								Es necesario que elijas una contraseña para tu cuenta. Recuerda,
								las reglas para crear tu nueva contraseña: <br /> <br />
								
								<ul>
									<li>Mínimo doce (12) caracteres de longitud</li>
									<li>Debe contener por lo menos un carácter alfabético y
										otro numérico; ya sea numérico o especial de los siguientes <b>(</b>
										#$%&/()=?_[]*;:.<b>).</b>
									<li>Debe contener por lo menos un carácter alfabético en
										minúsculas y uno en mayúsculas.</li>
									<li>No debe ser igual a las últimas diez (10) usadas.</li>
								</ul>
								
							</div>
						</div>
						
						<div class="alert alert-danger fade show" role="alert"
								ng-show="isActivationError && !isActivationSuccess">
								<div class="alert-icon">
									<i class="flaticon-close"></i>
								</div>
								<div class="alert-text">Lo sentimos, pero la información
									no es válida.</div>
								<div class="alert-close">
									<button type="button" class="close" data-dismiss="alert"
										aria-label="Close">
										<span aria-hidden="true"> <i class="la la-close"></i>
										</span>
									</button>
								</div>
							</div>

						<div class="alert alert-success fade show" role="alert"
							ng-show="isActivationSuccess">
							<div class="alert-icon">
								<i class="flaticon-confetti"></i>
							</div>
							<div class="alert-text">
								<p>Hemos actualizado correctamente tu
								contraseña, ahora inicia sesión.</p>
								<a href="/praga-web/login" class="btn btn-primary btn-block">
								ir a inicio de sesión </a>
							</div>
							<div class="alert-close">
								<button type="button" class="close" data-dismiss="alert"
									aria-label="Close">
									<span aria-hidden="true"> <i class="la la-check"></i>
									</span>
								</button>
							</div>
							
						</div>


						<div class="alert dark alert-danger alert-dismissible"
							role="alert" ng-show="addCredentialsError">
							<button type="button" class="close" data-dismiss="alert"
								aria-label="Cerrar">
								<span aria-hidden="true">×</span>
							</button>
							<i class="icon md-alert-circle-o" aria-hidden="true"></i>
							{{addCredentialsError}}
						</div>

						<form id="updateCredentialsForm" method="POST">
							
							<div class="row" ng-show="(isActivate || isActivationError) && !isActivationSuccess">
								<div class="col-md-12">
									<div class="form-group">
										<input type="password" 
											placeholder="contraseña" 
											class="form-control"
											name="passwordAdd" 
											maxlength="128"
											ng-model="user.password" 
											autocomplete="off"
											autofocus="autofocus">
									</div>
								</div>
								<div class="col-md-12">
									<div class="form-group">
										<input type="password" placeholder="confirma tu contraseña "
											class="form-control no-border" 
											name="passwordAddConfirm"
											maxlength="128"
											ng-model="confirmPassword"
											autocomplete="off">
									</div>
								</div>
							</div>
							<div ng-show=" (isActivate || isActivationError) && !isActivationSuccess">
								<button type="submit" 
									id="updateCredentialsBtn"
									class="btn btn-success btn-block">Aceptar</button>
							</div>
							
						</form>

					</div>
				</div>				
			</div>
		</div>
	</div>
</div>

