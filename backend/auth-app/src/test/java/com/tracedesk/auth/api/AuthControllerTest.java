package com.tracedesk.auth.api;

import com.tracedesk.auth.persistence.AuthStore;
import com.tracedesk.auth.application.ProjectAuthorization;
import com.tracedesk.auth.application.ProjectAccessRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockHttpServletResponse;
import com.tracedesk.auth.security.LoginBodyLimitFilter;

class AuthControllerTest {
 @Test void csrfRequiredBeforeCredentialsAndBadCredentialShapeIsUniform() throws Exception {
  AuthStore store=mock(AuthStore.class); PasswordEncoder encoder=new Argon2PasswordEncoder(16,32,1,19456,2);
  when(store.user("nobody@example.test")).thenReturn(Optional.empty());
  AuthController controller=controller(store,encoder);
  MockMvc mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiFailureHandler(store)).build();
  mvc.perform(post("/api/session/login").contentType("application/json").content("{\"login\":\"nobody@example.test\",\"password\":\"a-long-synthetic-password\"}"))
      .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
  verify(store).event("CSRF_INVALID","/api/session/login",403,null,null);
  MockHttpSession session=new MockHttpSession(); session.setAttribute(AuthController.CSRF,"known-token");
  var result=mvc.perform(post("/api/session/login").session(session).header("X-CSRF-TOKEN","known-token").contentType("application/json").content("{\"login\":\"nobody@example.test\",\"password\":\"a-long-synthetic-password\"}"))
      .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS")).andReturn();
  assertTrue(session.isInvalid());
  assertTrue(java.util.Arrays.stream(result.getResponse().getCookies()).anyMatch(c -> c.getName().equals("TDSESSION") && c.getMaxAge()==0 && c.getSecure() && c.isHttpOnly()));
  verify(store).event("LOGIN_DENIED","/api/session/login",401,null,null);
  verify(store,never()).user("NOBODY@example.test");
 }


 @Test void denialEventFailureStillRevokesExistingSession() throws Exception {
  AuthStore store=mock(AuthStore.class); PasswordEncoder encoder=new Argon2PasswordEncoder(16,32,1,19456,2);
  when(store.user("nobody@example.test")).thenReturn(Optional.empty());
  doThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic sink failure"))
      .when(store).event("LOGIN_DENIED","/api/session/login",401,null,null);
  MockMvc mvc=MockMvcBuilders.standaloneSetup(controller(store,encoder)).setControllerAdvice(new ApiFailureHandler(store)).build();
  MockHttpSession session=new MockHttpSession(); session.setAttribute(AuthController.CSRF,"known-token");
  var result=mvc.perform(post("/api/session/login").session(session).header("X-CSRF-TOKEN","known-token").contentType("application/json").content("{\"login\":\"nobody@example.test\",\"password\":\"a-long-synthetic-password\"}"))
      .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("SERVICE_UNAVAILABLE")).andReturn();
  assertTrue(session.isInvalid());
  assertTrue(java.util.Arrays.stream(result.getResponse().getCookies()).anyMatch(c -> c.getName().equals("TDSESSION") && c.getMaxAge()==0));
 }

 @Test void oversizedValidJsonIsRejectedBeforeDeserialization() throws Exception {
  AuthStore store=mock(AuthStore.class); PasswordEncoder encoder=new Argon2PasswordEncoder(16,32,1,19456,2);
  MockMvc mvc=MockMvcBuilders.standaloneSetup(controller(store,encoder)).setControllerAdvice(new ApiFailureHandler(store)).addFilters(new LoginBodyLimitFilter(store)).build();
  String body="{\"login\":\"nobody@example.test\",\"password\":\"a-long-synthetic-password\",\"padding\":\""+"x".repeat(8200)+"\"}";
  mvc.perform(post("/api/session/login").contentType("application/json").content(body))
      .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  verify(store).event("LOGIN_INVALID_REQUEST","/api/session/login",400,null,null);
  verify(store,never()).user(anyString());
 }

 @Test void unknownFieldsAreRequestErrors() throws Exception {
  AuthStore store=mock(AuthStore.class); PasswordEncoder encoder=new Argon2PasswordEncoder(16,32,1,19456,2);
  MockMvc mvc=MockMvcBuilders.standaloneSetup(controller(store,encoder)).setControllerAdvice(new ApiFailureHandler(store)).build();
  mvc.perform(post("/api/session/login").contentType("application/json").content("{\"login\":\"a@b\",\"password\":\"long-password-123\",\"role\":\"REPORTER\"}"))
      .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  verify(store).event("LOGIN_INVALID_REQUEST","/api/session/login",400,null,null);
 }

 @Test void projectReadUsesSessionActorAndHidesCrossProjectMembership() throws Exception {
  AuthStore store=mock(AuthStore.class);
  PasswordEncoder encoder=new Argon2PasswordEncoder(16,32,1,19456,2);
  UUID actor=UUID.randomUUID(), project=UUID.randomUUID(), forged=UUID.randomUUID();
  when(store.findProjectAccess(actor,project)).thenReturn(Optional.of(new ProjectAccessRepository.Snapshot(project,"Synthetic Project",true,true,true)));
  MockMvc mvc=MockMvcBuilders.standaloneSetup(controller(store,encoder)).setControllerAdvice(new ApiFailureHandler(store)).build();
  MockHttpSession session=authenticatedSession(actor);
  mvc.perform(get("/api/projects/{projectId}",project).session(session).param("userId",forged.toString()).header("X-Role","ADMIN"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(project.toString())).andExpect(jsonPath("$.name").value("Synthetic Project"));
  verify(store).findProjectAccess(actor,project);
  verify(store,never()).findProjectAccess(forged,project);

  UUID otherProject=UUID.randomUUID();
  mvc.perform(get("/api/projects/{projectId}",otherProject).session(session))
      .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  verify(store).event("PROJECT_DENIED","/api/projects/{projectId}",404,actor,otherProject);
 }

 @Test void projectDenialFailsClosedWhenSecurityEventStoreIsUnavailable() throws Exception {
  AuthStore store=mock(AuthStore.class);
  PasswordEncoder encoder=new Argon2PasswordEncoder(16,32,1,19456,2);
  UUID actor=UUID.randomUUID(), project=UUID.randomUUID();
  when(store.findProjectAccess(actor,project)).thenReturn(Optional.empty());
  doThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic sink failure"))
      .when(store).event("PROJECT_DENIED","/api/projects/{projectId}",404,actor,project);
  MockMvc mvc=MockMvcBuilders.standaloneSetup(controller(store,encoder)).setControllerAdvice(new ApiFailureHandler(store)).build();
  mvc.perform(get("/api/projects/{projectId}",project).session(authenticatedSession(actor)))
      .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("SERVICE_UNAVAILABLE"))
      .andExpect(jsonPath("$.name").doesNotExist());
 }

 @Test void logoutRequiresCsrfAndInvalidatesSessionReplay() throws Exception {
  AuthStore store=mock(AuthStore.class);
  PasswordEncoder encoder=new Argon2PasswordEncoder(16,32,1,19456,2);
  UUID actor=UUID.randomUUID();
  MockMvc mvc=MockMvcBuilders.standaloneSetup(controller(store,encoder)).setControllerAdvice(new ApiFailureHandler(store)).build();
  MockHttpSession session=authenticatedSession(actor);
  mvc.perform(post("/api/session/logout").session(session))
      .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
  assertTrue(!session.isInvalid());
  mvc.perform(post("/api/session/logout").session(session).header("X-CSRF-TOKEN","known-token"))
      .andExpect(status().isNoContent());
  assertTrue(session.isInvalid());
  verify(store).event("CSRF_INVALID","/api/session/logout",403,actor,null);
 }

 @Test void successfulLoginRotatesSessionAndReplacesCsrfToken() throws Exception {
  AuthStore store=mock(AuthStore.class);
  PasswordEncoder encoder=new Argon2PasswordEncoder(16,32,1,19456,2);
  String password="a-long-synthetic-password";
  UUID actor=UUID.randomUUID();
  when(store.user("user@example.test")).thenReturn(Optional.of(new AuthStore.User(actor,encoder.encode(password),true)));
  MockMvc mvc=MockMvcBuilders.standaloneSetup(controller(store,encoder)).setControllerAdvice(new ApiFailureHandler(store)).build();
  MockHttpSession session=new MockHttpSession();
  session.setAttribute(AuthController.CSRF,"known-token");
  String originalSessionId=session.getId();
  mvc.perform(post("/api/session/login").session(session).header("X-CSRF-TOKEN","known-token")
          .contentType("application/json").content("{\"login\":\"user@example.test\",\"password\":\""+password+"\"}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(actor.toString()));
  assertNotEquals(originalSessionId,session.getId());
  assertEquals(actor,session.getAttribute(AuthController.USER));
  assertNotEquals("known-token",session.getAttribute(AuthController.CSRF));
 }

 private static MockHttpSession authenticatedSession(UUID actor) {
  MockHttpSession session=new MockHttpSession();
  session.setAttribute(AuthController.USER,actor);
  session.setAttribute(AuthController.AUTH_AT,System.currentTimeMillis());
  session.setAttribute(AuthController.CSRF,"known-token");
  return session;
 }
 private static AuthController controller(AuthStore store, PasswordEncoder encoder) {
  return new AuthController(store,new ProjectAuthorization(store),encoder);
 }
}
