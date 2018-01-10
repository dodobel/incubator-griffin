/*
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
*/

package org.apache.griffin.core.login;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

@Service
public class LoginServiceImpl implements LoginService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoginServiceImpl.class);

    private static final String LDAP_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";

    private String strategy;
    private String url;
    private String email;
    private String searchBase;
    private String searchPattern;
    private SearchControls searchControls;

    public LoginServiceImpl(@Value("${login.strategy}") String strategy,
                            @Value("${ldap.url}") String url,
                            @Value("${ldap.email}") String email,
                            @Value("${ldap.searchBase}") String searchBase,
                            @Value("${ldap.searchPattern}") String searchPattern) throws Exception {
        this.strategy = strategy;
        if (strategy.equals("ldap")) {
            this.url = url;
            this.email = email;
            this.searchBase = searchBase;
            this.searchPattern = searchPattern;
            SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            this.searchControls = searchControls;
        } else if (!strategy.equals("test")) {
            throw new Exception("Missing login strategy configuration");
        }
    }

    @Override
    public ResponseEntity<Map<String, Object>> login(Map<String, String> map) {
        if (strategy.equals("test")) {
            return getResponse("test", "test");
        }
        return loginLDAP(map);
    }

    private ResponseEntity<Map<String, Object>> loginLDAP(Map<String, String> map) {
        String ntAccount = map.get("username");
        String password = map.get("password");
        String searchFilter = searchPattern.replace("{0}", ntAccount);
        try {
            LdapContext ctx = getContextInstance(ntAccount, password);
            NamingEnumeration<SearchResult> results = ctx.search(searchBase, searchFilter, searchControls);
            String fullName = getFullName(results);
            if (fullName == null) {
                fullName = ntAccount;
            }
            return getResponse(ntAccount, fullName);
        } catch (NamingException e) {
            LOGGER.warn("Failed to login with LDAP auth. {}", e.getMessage());
        }
        return null;
    }

    private String getFullName(NamingEnumeration<SearchResult> results) throws NamingException {
        String fullName = null;
        while (results.hasMoreElements()) {
            SearchResult searchResult = results.nextElement();
            Attributes attrs = searchResult.getAttributes();
            if (attrs != null && attrs.get("cn") != null) {
                String cnName = (String) attrs.get("cn").get();
                if (cnName.indexOf("(") > 0) {
                    fullName = cnName.substring(0, cnName.indexOf("("));
                }
            }
        }
        return fullName;
    }

    private LdapContext getContextInstance(String ntAccount, String password) throws NamingException {
        Hashtable<String, String> ht = new Hashtable<>();
        ht.put(Context.INITIAL_CONTEXT_FACTORY, LDAP_FACTORY);
        ht.put(Context.PROVIDER_URL, url);
        ht.put(Context.SECURITY_PRINCIPAL, ntAccount + email);
        ht.put(Context.SECURITY_CREDENTIALS, password);
        return new InitialLdapContext(ht, null);
    }

    private ResponseEntity<Map<String, Object>> getResponse(String ntAccount, String fullName) {
        if (fullName != null) {
            Map<String, Object> message = new HashMap<>();
            message.put("ntAccount", ntAccount);
            message.put("fullName", fullName);
            message.put("status", 0);
            return new ResponseEntity<>(message, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}