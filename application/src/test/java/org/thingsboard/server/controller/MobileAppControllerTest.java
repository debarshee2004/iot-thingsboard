/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.server.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.mobile.MobileApp;
import org.thingsboard.server.common.data.mobile.MobileAppInfo;
import org.thingsboard.server.common.data.oauth2.MapperType;
import org.thingsboard.server.common.data.oauth2.OAuth2Client;
import org.thingsboard.server.common.data.oauth2.OAuth2ClientInfo;
import org.thingsboard.server.common.data.oauth2.OAuth2CustomMapperConfig;
import org.thingsboard.server.common.data.oauth2.OAuth2MapperConfig;
import org.thingsboard.server.common.data.oauth2.PlatformType;
import org.thingsboard.server.dao.service.DaoSqlTest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@DaoSqlTest
public class MobileAppControllerTest extends AbstractControllerTest {

    @Before
    public void setUp() throws Exception {
        loginSysAdmin();
    }

    @After
    public void tearDown() throws Exception {
        List<MobileAppInfo> mobileAppInfos = doGetTyped("/api/mobileApp/infos", new TypeReference<List<MobileAppInfo>>() {
        });
        for (MobileApp mobileApp : mobileAppInfos) {
            doDelete("/api/mobileApp/" + mobileApp.getId().getId())
                    .andExpect(status().isOk());
        }

        List<OAuth2ClientInfo> oAuth2ClientInfos = doGetTyped("/api/oauth2/client/infos", new TypeReference<List<OAuth2ClientInfo>>() {
        });
        for (OAuth2ClientInfo oAuth2ClientInfo : oAuth2ClientInfos) {
            doDelete("/api/oauth2/client/" + oAuth2ClientInfo.getId().getId().toString())
                    .andExpect(status().isOk());
        }
    }

    @Test
    public void testSaveMobileApp() throws Exception {
        List<MobileAppInfo> MobileAppInfos = doGetTyped("/api/mobileApp/infos", new TypeReference<List<MobileAppInfo>>() {
        });
        assertThat(MobileAppInfos).isEmpty();

        MobileApp mobileApp = validMobileApp(TenantId.SYS_TENANT_ID, "my.test.package", true);
        MobileApp savedMobileApp = doPost("/api/mobileApp", mobileApp, MobileApp.class);

        List<MobileAppInfo> MobileAppInfos2 = doGetTyped("/api/mobileApp/infos", new TypeReference<List<MobileAppInfo>>() {
        });
        assertThat(MobileAppInfos2).hasSize(1);
        assertThat(MobileAppInfos2.get(0)).isEqualTo(new MobileAppInfo(savedMobileApp, Collections.emptyList()));

        MobileAppInfo retrievedMobileAppInfo = doGet("/api/mobileApp/info/{id}", MobileAppInfo.class, savedMobileApp.getId().getId());
        assertThat(retrievedMobileAppInfo).isEqualTo(new MobileAppInfo(savedMobileApp, Collections.emptyList()));

        doDelete("/api/mobileApp/" + savedMobileApp.getId().getId());
        doGet("/api/mobileApp/info/{id}", savedMobileApp.getId().getId())
                .andExpect(status().isNotFound());
    }

    @Test
    public void testSaveMobileAppWithShortAppSecret() throws Exception {
        MobileApp mobileApp = validMobileApp(TenantId.SYS_TENANT_ID, "mobileApp.ce", true);
        mobileApp.setAppSecret("short");
        doPost("/api/mobileApp", mobileApp)
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString("appSecret must be at least 16 characters")));
    }

    @Test
    public void testUpdateMobileAppOauth2Clients() throws Exception {
        MobileApp mobileApp = validMobileApp(TenantId.SYS_TENANT_ID, "my.test.package", true);
        MobileApp savedMobileApp = doPost("/api/mobileApp", mobileApp, MobileApp.class);

        OAuth2Client oAuth2Client = validClientInfo(TenantId.SYS_TENANT_ID, "test google client");
        OAuth2Client savedOAuth2Client = doPost("/api/oauth2/client", oAuth2Client, OAuth2Client.class);

        OAuth2Client oAuth2Client2 = validClientInfo(TenantId.SYS_TENANT_ID, "test facebook client");
        OAuth2Client savedOAuth2Client2 = doPost("/api/oauth2/client", oAuth2Client2, OAuth2Client.class);

        doPut("/api/mobileApp/" + savedMobileApp.getId() + "/oauth2Clients", List.of(savedOAuth2Client.getId().getId(), savedOAuth2Client2.getId().getId()));

        MobileAppInfo retrievedMobileAppInfo = doGet("/api/mobileApp/info/{id}", MobileAppInfo.class, savedMobileApp.getId().getId());
        assertThat(retrievedMobileAppInfo).isEqualTo(new MobileAppInfo(savedMobileApp, List.of(new OAuth2ClientInfo(savedOAuth2Client),
                new OAuth2ClientInfo(savedOAuth2Client2))));

        doPut("/api/mobileApp/" + savedMobileApp.getId() + "/oauth2Clients", List.of(savedOAuth2Client2.getId().getId()));
        MobileAppInfo retrievedMobileAppInfo2 = doGet("/api/mobileApp/info/{id}", MobileAppInfo.class, savedMobileApp.getId().getId());
        assertThat(retrievedMobileAppInfo2).isEqualTo(new MobileAppInfo(savedMobileApp, List.of(new OAuth2ClientInfo(savedOAuth2Client2))));
    }

    @Test
    public void testCreateMobileAppWithOauth2Clients() throws Exception {
        OAuth2Client oAuth2Client = validClientInfo(TenantId.SYS_TENANT_ID, "test google client");
        OAuth2Client savedOAuth2Client = doPost("/api/oauth2/client", oAuth2Client, OAuth2Client.class);

        MobileApp mobileApp = validMobileApp(TenantId.SYS_TENANT_ID, "my.test.package", true);
        MobileApp savedMobileApp = doPost("/api/mobileApp?oauth2ClientIds=" + savedOAuth2Client.getId().getId(), mobileApp, MobileApp.class);

        MobileAppInfo retrievedMobileAppInfo = doGet("/api/mobileApp/info/{id}", MobileAppInfo.class, savedMobileApp.getId().getId());
        assertThat(retrievedMobileAppInfo).isEqualTo(new MobileAppInfo(savedMobileApp, List.of(new OAuth2ClientInfo(savedOAuth2Client))));
    }

    private MobileApp validMobileApp(TenantId tenantId, String mobileAppName, boolean oauth2Enabled) {
        MobileApp MobileApp = new MobileApp();
        MobileApp.setTenantId(tenantId);
        MobileApp.setPkgName(mobileAppName);
        MobileApp.setAppSecret(StringUtils.randomAlphanumeric(24));
        MobileApp.setOauth2Enabled(oauth2Enabled);
        return MobileApp;
    }

    protected OAuth2Client validClientInfo(TenantId tenantId, String title) {
        return validClientInfo(tenantId, title, null);
    }

    protected OAuth2Client validClientInfo(TenantId tenantId, String title, List<PlatformType> platforms) {
        OAuth2Client oAuth2Client = new OAuth2Client();
        oAuth2Client.setTenantId(tenantId);
        oAuth2Client.setTitle(title);
        oAuth2Client.setClientId(UUID.randomUUID().toString());
        oAuth2Client.setClientSecret(UUID.randomUUID().toString());
        oAuth2Client.setAuthorizationUri(UUID.randomUUID().toString());
        oAuth2Client.setAccessTokenUri(UUID.randomUUID().toString());
        oAuth2Client.setScope(Arrays.asList(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        oAuth2Client.setPlatforms(platforms == null ? Collections.emptyList() : platforms);
        oAuth2Client.setUserInfoUri(UUID.randomUUID().toString());
        oAuth2Client.setUserNameAttributeName(UUID.randomUUID().toString());
        oAuth2Client.setJwkSetUri(UUID.randomUUID().toString());
        oAuth2Client.setClientAuthenticationMethod(UUID.randomUUID().toString());
        oAuth2Client.setLoginButtonLabel(UUID.randomUUID().toString());
        oAuth2Client.setLoginButtonIcon(UUID.randomUUID().toString());
        oAuth2Client.setAdditionalInfo(JacksonUtil.newObjectNode().put(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        oAuth2Client.setMapperConfig(
                OAuth2MapperConfig.builder()
                        .allowUserCreation(true)
                        .activateUser(true)
                        .type(MapperType.CUSTOM)
                        .custom(
                                OAuth2CustomMapperConfig.builder()
                                        .url(UUID.randomUUID().toString())
                                        .build()
                        )
                        .build());
        return oAuth2Client;
    }

}
