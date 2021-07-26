/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.security.module.configuration;

import com.evolveum.midpoint.prism.crypto.EncryptionException;
import com.evolveum.midpoint.prism.crypto.Protector;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.security.util.KeyStoreKey;
import com.evolveum.midpoint.web.security.util.MidpointSamlLocalServiceProviderConfiguration;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.ProtectedStringType;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;
import org.opensaml.security.x509.X509Support;
import org.springframework.security.saml.SamlKeyException;
import org.springframework.security.saml.key.KeyType;
import org.springframework.security.saml.key.SimpleKey;
import org.springframework.security.saml.provider.SamlServerConfiguration;
import org.springframework.security.saml.provider.config.NetworkConfiguration;
import org.springframework.security.saml.provider.config.RotatingKeys;
import org.springframework.security.saml.provider.service.config.ExternalIdentityProviderConfiguration;
import org.springframework.security.saml.provider.service.config.LocalServiceProviderConfiguration;
import org.springframework.security.saml.saml2.signature.AlgorithmMethod;
import org.springframework.security.saml.saml2.signature.DigestMethod;
import org.springframework.security.saml.util.X509Utilities;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.registration.*;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.CharArrayReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;

import static java.util.Optional.ofNullable;
import static org.springframework.security.saml.util.StringUtils.stripSlashes;
import static org.springframework.util.StringUtils.hasText;

/**
 * @author skublik
 */

public class SamlModuleWebSecurityConfiguration extends ModuleWebSecurityConfigurationImpl {

    private static final Trace LOGGER = TraceManager.getTrace(SamlModuleWebSecurityConfiguration.class);

    public static final String RESPONSE_PROCESSING_URL_SUFFIX = "/SSO/alias/{registrationId}";
    public static final String REQUEST_PROCESSING_URL_SUFFIX = "/authenticate/{registrationId}";

    private static Protector protector;

    private SamlServerConfiguration samlConfiguration;
    private RelyingPartyRegistrationRepository relyingPartyRegistrationRepository;
    private Map<String, SamlMidpointAdditionalConfiguration> additionalConfiguration = new HashMap<String, SamlMidpointAdditionalConfiguration>();

    private SamlModuleWebSecurityConfiguration() {
    }

    public static void setProtector(Protector protector) {
        SamlModuleWebSecurityConfiguration.protector = protector;
    }

    public static SamlModuleWebSecurityConfiguration build(Saml2AuthenticationModuleType modelType, String prefixOfSequence,
            String publicHttpUrlPattern, ServletRequest request) {
        SamlModuleWebSecurityConfiguration configuration = buildInternal(modelType, prefixOfSequence, publicHttpUrlPattern, request);
        configuration.validate();
        return configuration;
    }

    private static SamlModuleWebSecurityConfiguration buildInternal(Saml2AuthenticationModuleType modelType, String prefixOfSequence, String publicHttpUrlPattern,
            ServletRequest request) {
        SamlModuleWebSecurityConfiguration configuration = new SamlModuleWebSecurityConfiguration();
        build(configuration, modelType, prefixOfSequence);
        SamlServerConfiguration samlConfiguration = new SamlServerConfiguration();

        Saml2NetworkAuthenticationModuleType networkType = modelType.getNetwork();
        if (networkType != null) {
            NetworkConfiguration network = new NetworkConfiguration();
            if (networkType.getConnectTimeout() != 0) {
                network.setConnectTimeout(networkType.getConnectTimeout());
            }
            if (networkType.getReadTimeout() != 0) {
                network.setReadTimeout(networkType.getReadTimeout());
            }
            samlConfiguration.setNetwork(network);
        }
        Saml2ServiceProviderAuthenticationModuleType serviceProviderType = modelType.getServiceProvider();
        MidpointSamlLocalServiceProviderConfiguration serviceProvider = new MidpointSamlLocalServiceProviderConfiguration();
        serviceProvider.setEntityId(serviceProviderType.getEntityId())
//                .setSignMetadata(Boolean.TRUE.equals(serviceProviderType.isSignRequests()))
//                .setSignRequests(Boolean.TRUE.equals(serviceProviderType.isSignRequests()))
                .setWantAssertionsSigned(Boolean.TRUE.equals(serviceProviderType.isWantAssertionsSigned()))
                .setSingleLogoutEnabled(Boolean.TRUE.equals(serviceProviderType.isSingleLogoutEnabled()));
//        if (StringUtils.isNotBlank(publicHttpUrlPattern)) {
//            serviceProvider.setBasePath(publicHttpUrlPattern);
//        } else {
//            serviceProvider.setBasePath(getBasePath(((HttpServletRequest) request)));
//        }

        List<Object> objectList = new ArrayList<Object>();
        for (Saml2NameIdAuthenticationModuleType nameIdType : serviceProviderType.getNameId()) {
            objectList.add(nameIdType.value());
        }
        serviceProvider.setNameIds(objectList);
        if (serviceProviderType.getDefaultDigest() != null) {
            serviceProvider.setDefaultDigest(DigestMethod.fromUrn(serviceProviderType.getDefaultDigest().value()));
        }
        if (serviceProviderType.getDefaultSigningAlgorithm() != null) {
            serviceProvider.setDefaultSigningAlgorithm(AlgorithmMethod.fromUrn(serviceProviderType.getDefaultSigningAlgorithm().value()));
        }
        Saml2KeyAuthenticationModuleType keysType = serviceProviderType.getKeys();
        RotatingKeys key = new RotatingKeys();
        if (keysType != null) {
            ModuleSaml2SimpleKeyType activeSimpleKey = keysType.getActiveSimpleKey();
            if (activeSimpleKey != null) {
                try {
                    key.setActive(createSimpleKey(activeSimpleKey));
                } catch (EncryptionException e) {
                    LOGGER.error("Couldn't obtain clear string for configuration of SimpleKey from " + activeSimpleKey);
                }
            }
            ModuleSaml2KeyStoreKeyType activeKeyStoreKey = keysType.getActiveKeyStoreKey();
            if (activeKeyStoreKey != null) {
                try {
                    key.setActive(createKeyStoreKey(activeKeyStoreKey));
                } catch (EncryptionException e) {
                    LOGGER.error("Couldn't obtain clear string for configuration of KeyStoreKey from " + activeKeyStoreKey);
                }
            }

            if (keysType.getStandBySimpleKey() != null && !keysType.getStandBySimpleKey().isEmpty()) {
                for (ModuleSaml2SimpleKeyType standByKey : keysType.getStandBySimpleKey()) {
                    try {
                        key.getStandBy().add(createSimpleKey(standByKey));
                    } catch (EncryptionException e) {
                        LOGGER.error("Couldn't obtain clear string for configuration of SimpleKey from " + standByKey);
                    }
                }
            }
            if (keysType.getStandByKeyStoreKey() != null && !keysType.getStandByKeyStoreKey().isEmpty()) {
                for (ModuleSaml2KeyStoreKeyType standByKey : keysType.getStandByKeyStoreKey()) {
                    try {
                        key.getStandBy().add(createKeyStoreKey(standByKey));
                    } catch (EncryptionException e) {
                        LOGGER.error("Couldn't obtain clear string for configuration of SimpleKey from " + standByKey);
                    }
                }
            }
        }
        serviceProvider.setKeys(key);
        serviceProvider.setAlias(serviceProviderType.getAlias());
        serviceProvider.setAliasForPath(serviceProviderType.getAliasForPath());

        List<ExternalIdentityProviderConfiguration> providers = new ArrayList<ExternalIdentityProviderConfiguration>();
        List<Saml2ProviderAuthenticationModuleType> providersType = serviceProviderType.getProvider();
        List<RelyingPartyRegistration> registrations = new ArrayList<>();
        for (Saml2ProviderAuthenticationModuleType providerType : providersType) {
            String registrationId = StringUtils.isNotEmpty(serviceProviderType.getAliasForPath()) ? serviceProviderType.getAliasForPath() :
                    (StringUtils.isNotEmpty(serviceProviderType.getAlias()) ? serviceProviderType.getAlias() : serviceProviderType.getEntityId());
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(
                    StringUtils.isNotBlank(publicHttpUrlPattern) ? publicHttpUrlPattern : getBasePath((HttpServletRequest) request));
            builder.pathSegment(stripSlashes(configuration.getPrefix()) + RESPONSE_PROCESSING_URL_SUFFIX);
            RelyingPartyRegistration.Builder registrationBuilder = getRelyingPartyFromMetadata(providerType.getMetadata())
                    .registrationId(registrationId)
                    .entityId(serviceProviderType.getEntityId())
                    .assertionConsumerServiceLocation(builder.build().toUriString())
                    .assertingPartyDetails(party -> {
                        party.entityId(providerType.getEntityId())
                                .singleSignOnServiceBinding(Saml2MessageBinding.from(providerType.getAuthenticationRequestBinding()));
                        if (serviceProviderType.isSignRequests() != null) {
                            party.wantAuthnRequestsSigned(Boolean.TRUE.equals(serviceProviderType.isSignRequests()));
                        }
                    });
            Saml2X509Credential activeCredential = null;
            ModuleSaml2SimpleKeyType simpleKeyType = keysType.getActiveSimpleKey();
            if (simpleKeyType != null) {
                activeCredential = getSaml2Credential(simpleKeyType, true);
            }
            ModuleSaml2KeyStoreKeyType storeKeyType = keysType.getActiveKeyStoreKey();
            if (storeKeyType != null) {
                activeCredential = getSaml2Credential(storeKeyType, true);
            }

            List<Saml2X509Credential> credentials = new ArrayList<>();
            if (activeCredential != null) {
                credentials.add(activeCredential);
            }

            if (keysType.getStandBySimpleKey() != null && !keysType.getStandBySimpleKey().isEmpty()) {
                for (ModuleSaml2SimpleKeyType standByKey : keysType.getStandBySimpleKey()) {
                    Saml2X509Credential credential = getSaml2Credential(standByKey, false);
                    if (credential != null) {
                        credentials.add(credential);
                    }
                }
            }
            if (keysType.getStandByKeyStoreKey() != null && !keysType.getStandByKeyStoreKey().isEmpty()) {
                for (ModuleSaml2KeyStoreKeyType standByKey : keysType.getStandByKeyStoreKey()) {
                    Saml2X509Credential credential = getSaml2Credential(standByKey, false);
                    if (credential != null) {
                        credentials.add(credential);
                    }
                }
            }

            if (!credentials.isEmpty()) {
                registrationBuilder.decryptionX509Credentials(c -> {
                    c.addAll(credentials);
                });
                registrationBuilder.signingX509Credentials(c -> {
                    c.addAll(credentials);
                });
            }
            registrations.add(registrationBuilder.build());

            ExternalIdentityProviderConfiguration provider = new ExternalIdentityProviderConfiguration();
            provider.setAlias(providerType.getAlias())
                    .setSkipSslValidation(Boolean.TRUE.equals(providerType.isSkipSslValidation()))
                    .setMetadataTrustCheck(Boolean.TRUE.equals(providerType.isMetadataTrustCheck()))
                    .setAuthenticationRequestBinding(URI.create(providerType.getAuthenticationRequestBinding()));
            if (StringUtils.isNotBlank(providerType.getLinkText())) {
                provider.setLinktext(providerType.getLinkText());
            }
            List<String> verificationKeys = new ArrayList<String>();
            for (ProtectedStringType verificationKeyProtected : providerType.getVerificationKeys()) {
                try {
                    String verificationKey = protector.decryptString(verificationKeyProtected);
                    verificationKeys.add(verificationKey);
                } catch (EncryptionException e) {
                    LOGGER.error("Couldn't obtain clear string for provider verification key");
                }
            }
            if (verificationKeys != null && !verificationKeys.isEmpty()) {
                provider.setVerificationKeys(verificationKeys);
            }
            try {
                provider.setMetadata(createMetadata(providerType.getMetadata(), true));
            } catch (Exception e) {
                LOGGER.error("Couldn't obtain metadata as string from " + providerType.getMetadata());
            }
            providers.add(provider);
            String linkText = providerType.getLinkText() == null ?
                    (providerType.getAlias() == null ? providerType.getEntityId() : providerType.getAlias())
                    : providerType.getLinkText();
            configuration.additionalConfiguration.put(providerType.getEntityId(),
                    SamlMidpointAdditionalConfiguration.builder()
                            .nameOfUsernameAttribute(providerType.getNameOfUsernameAttribute())
                            .linkText(linkText)
                            .build()
            );
        }

        RelyingPartyRegistrationRepository relyingPartyRegistrationRepository = new InMemoryRelyingPartyRegistrationRepository(registrations);

        serviceProvider.setProviders(providers);
        try {
            serviceProvider.setMetadata(createMetadata(serviceProviderType.getMetadata(), false));
        } catch (Exception e) {
            LOGGER.error("Couldn't obtain metadata as string from " + serviceProviderType.getMetadata());
        }
        serviceProvider.setPrefix(configuration.getPrefix());
        samlConfiguration.setServiceProvider(serviceProvider);
        configuration.setSamlConfiguration(samlConfiguration);
        configuration.setRelyingPartyRegistrationRepository(relyingPartyRegistrationRepository);
        return configuration;
    }

    private static RelyingPartyRegistration.Builder getRelyingPartyFromMetadata(Saml2ProviderMetadataAuthenticationModuleType metadata) {
        RelyingPartyRegistration.Builder builder = RelyingPartyRegistration.withRegistrationId("builder");
        if (metadata != null) {
            if (metadata.getXml() != null || metadata.getPathToFile() != null) {
                String metadataAsString = null;
                try {
                    metadataAsString = createMetadata(metadata, true);
                } catch (IOException e) {
                    LOGGER.error("Couldn't obtain metadata as string from " + metadata);
                }
                builder = RelyingPartyRegistrations.fromMetadata(new ByteArrayInputStream(metadataAsString.getBytes()));
            }
            if (metadata.getMetadataUrl() != null) {
                builder = RelyingPartyRegistrations.fromMetadataLocation(metadata.getMetadataUrl());
            }
        }
        return builder;
    }

    private static String createMetadata(Saml2ProviderMetadataAuthenticationModuleType metadata, boolean required) throws IOException {
        if (metadata != null) {
            String metadataUrl = metadata.getMetadataUrl();
            if (StringUtils.isNotBlank(metadataUrl)) {
                return metadataUrl;
            }
            String pathToFile = metadata.getPathToFile();
            if (StringUtils.isNotBlank(pathToFile)) {
                return readFile(pathToFile);
            }
            byte[] xml = metadata.getXml();
            if (xml != null && xml.length != 0) {
                return new String(xml);
            }
        }
        if (required) {
            throw new IllegalArgumentException("Metadata is not present");
        }
        return null;
    }

    private static String readFile(String path) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded);
    }

    private static SimpleKey createSimpleKey(ModuleSaml2SimpleKeyType simpleKeyType) throws EncryptionException {
        SimpleKey key = new SimpleKey();
        key.setName(simpleKeyType.getName());
//        Protector protector = ((MidPointApplication) Application.get()).getProtector();
        String privateKey = protector.decryptString(simpleKeyType.getPrivateKey());
        key.setPrivateKey(privateKey);
        String passphrase = protector.decryptString(simpleKeyType.getPassphrase());
        key.setPassphrase(passphrase);
        String certificate = protector.decryptString(simpleKeyType.getCertificate());
        key.setCertificate(certificate);
        if (simpleKeyType.getType() != null) {
            key.setType(KeyType.fromTypeName(simpleKeyType.getType().name()));
        }
        return key;
    }

    private static KeyStoreKey createKeyStoreKey(ModuleSaml2KeyStoreKeyType keyStoreKeyType) throws EncryptionException {
        KeyStoreKey key = new KeyStoreKey();
        key.setKeyAlias(keyStoreKeyType.getKeyAlias());
        //        Protector protector = ((MidPointApplication) Application.get()).getProtector();
        if (keyStoreKeyType.getKeyPassword() != null) {
            String keyPassword = protector.decryptString(keyStoreKeyType.getKeyPassword());
            key.setKeyPassword(keyPassword);
        }
        String keyStorePath = keyStoreKeyType.getKeyStorePath();
        key.setKeyStorePath(keyStorePath);
        String keyStorePassword = protector.decryptString(keyStoreKeyType.getKeyStorePassword());
        key.setKeyStorePassword(keyStorePassword);
        if (keyStoreKeyType.getType() != null) {
            key.setType(KeyType.fromTypeName(keyStoreKeyType.getType().name()));
        }
        return key;
    }

    private static String getBasePath(HttpServletRequest request) {
        boolean includePort = true;
        if (443 == request.getServerPort() && "https".equals(request.getScheme())) {
            includePort = false;
        } else if (80 == request.getServerPort() && "http".equals(request.getScheme())) {
            includePort = false;
        }
        return request.getScheme() +
                "://" +
                request.getServerName() +
                (includePort ? (":" + request.getServerPort()) : "") +
                request.getContextPath();
    }

    public SamlServerConfiguration getSamlConfiguration() {
        return samlConfiguration;
    }

    public void setSamlConfiguration(SamlServerConfiguration samlConfiguration) {
        this.samlConfiguration = samlConfiguration;
    }

    public RelyingPartyRegistrationRepository getRelyingPartyRegistrationRepository() {
        return relyingPartyRegistrationRepository;
    }

    public void setRelyingPartyRegistrationRepository(RelyingPartyRegistrationRepository relyingPartyRegistrationRepository) {
        this.relyingPartyRegistrationRepository = relyingPartyRegistrationRepository;
    }

    public Map<String, SamlMidpointAdditionalConfiguration> getAdditionalConfiguration() {
        return additionalConfiguration;
    }

    @Override
    protected void validate() {
        super.validate();
        if (getSamlConfiguration() == null) {
            throw new IllegalArgumentException("Saml configuration is null");
        }
    }

    public static Saml2X509Credential getSaml2Credential(ModuleSaml2SimpleKeyType key, boolean isActive) {
        if (key == null) {
            return null;
        }
        try {
            Saml2X509Credential saml2credential = null;
            byte[] certbytes = X509Utilities.getDER(protector.decryptString(key.getCertificate()));
            X509Certificate certificate = X509Utilities.getCertificate(certbytes);

            String stringPrivateKey = protector.decryptString(key.getPrivateKey());
            String stringPassphrase = protector.decryptString(key.getPassphrase());
            if (hasText(stringPrivateKey)) {
                PrivateKey pkey;
                Object obj = null;
                try {
                    PEMParser parser = new PEMParser(new CharArrayReader(stringPrivateKey.toCharArray()));
                    obj = parser.readObject();
                    parser.close();
                    JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
                    if (obj == null) {
                        throw new SamlKeyException("Unable to decode PEM key:" + key.getPrivateKey());
                    } else if (obj instanceof PEMEncryptedKeyPair) {

                        // Encrypted key - we will use provided password
                        PEMEncryptedKeyPair ckp = (PEMEncryptedKeyPair) obj;
                        char[] passarray = (ofNullable(stringPassphrase).orElse("")).toCharArray();
                        PEMDecryptorProvider decProv = new JcePEMDecryptorProviderBuilder().build(passarray);
                        KeyPair kp = converter.getKeyPair(ckp.decryptKeyPair(decProv));
                        pkey = kp.getPrivate();
                    } else if (obj instanceof PEMKeyPair) {
                        // Unencrypted key - no password needed
                        PEMKeyPair ukp = (PEMKeyPair) obj;
                        KeyPair kp = converter.getKeyPair(ukp);
                        pkey = kp.getPrivate();
                    } else if (obj instanceof PrivateKeyInfo) {
                        // Encrypted key - we will use provided password
                        PrivateKeyInfo pk = (PrivateKeyInfo) obj;
                        pkey = converter.getPrivateKey(pk);
                    } else if (obj instanceof PKCS8EncryptedPrivateKeyInfo) {
                        // Encrypted key - we will use provided password
                        PKCS8EncryptedPrivateKeyInfo cpk = (PKCS8EncryptedPrivateKeyInfo) obj;
                        char[] passarray = (ofNullable(stringPassphrase).orElse("")).toCharArray();
                        final InputDecryptorProvider provider = new JceOpenSSLPKCS8DecryptorProviderBuilder().build(passarray);
                        pkey = converter.getPrivateKey(cpk.decryptPrivateKeyInfo(provider));
                    } else {
                        throw new SamlKeyException("Unable get private key from " + obj);
                    }
                } catch (IOException e) {
                    throw new SamlKeyException("Unable get private key", e);
                } catch (OperatorCreationException | PKCSException e) {
                    throw new SamlKeyException("Unable get private key from " + obj, e);
                }
                List<Saml2X509Credential.Saml2X509CredentialType> types = getTypesForKey(isActive, key.getType());
                saml2credential = new Saml2X509Credential(pkey, certificate, types.toArray(new Saml2X509Credential.Saml2X509CredentialType[0]));
            }

            return saml2credential;
        } catch (EncryptionException | CertificateException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static List<Saml2X509Credential.Saml2X509CredentialType> getTypesForKey(boolean isActive, ModuleSaml2KeyTypeType type) {
        List<Saml2X509Credential.Saml2X509CredentialType> types = new ArrayList<>();
        if (isActive) {
            types.add(Saml2X509Credential.Saml2X509CredentialType.SIGNING);
            types.add(Saml2X509Credential.Saml2X509CredentialType.DECRYPTION);
        } else if (type != null) {
            if (ModuleSaml2KeyTypeType.UNSPECIFIED.equals(type)) {
                types.add(Saml2X509Credential.Saml2X509CredentialType.SIGNING);
                types.add(Saml2X509Credential.Saml2X509CredentialType.DECRYPTION);
            } else {
                types.add(Saml2X509Credential.Saml2X509CredentialType.valueOf(type.name()));
            }
        } else {
            types.add(Saml2X509Credential.Saml2X509CredentialType.SIGNING);
            types.add(Saml2X509Credential.Saml2X509CredentialType.DECRYPTION);
        }
        return types;
    }

    public static Saml2X509Credential getSaml2Credential(ModuleSaml2KeyStoreKeyType key, boolean isActive) {
        if (key == null) {
            return null;
        }
        try {
            KeyStore ks = KeyStore.getInstance("JKS");
            FileInputStream is = new FileInputStream(key.getKeyStorePath());
            ks.load(is, protector.decryptString(key.getKeyStorePassword()).toCharArray());

            Key pkey = ks.getKey(key.getKeyAlias(), protector.decryptString(key.getKeyPassword()).toCharArray());
            if (!(pkey instanceof PrivateKey)) {
                throw new SamlKeyException("Alias " + key.getKeyAlias() + " don't return key of PrivateKey type.");
            }
            Certificate certificate = ks.getCertificate(key.getKeyAlias());
            if (!(certificate instanceof X509Certificate)) {
                throw new SamlKeyException("Alias " + key.getKeyAlias() + " don't return certificate of X509Certificate type.");
            }
            List<Saml2X509Credential.Saml2X509CredentialType> types = getTypesForKey(isActive, key.getType());
            return new Saml2X509Credential((PrivateKey) pkey, (X509Certificate) certificate, types.toArray(new Saml2X509Credential.Saml2X509CredentialType[0]));
        } catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException | EncryptionException | UnrecoverableKeyException e) {
            throw new SamlKeyException(e);
        }
    }
}
