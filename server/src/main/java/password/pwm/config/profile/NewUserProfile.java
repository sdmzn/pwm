/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

package password.pwm.config.profile;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequestContext;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.password.PasswordUtility;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class NewUserProfile extends AbstractProfile implements Profile
{

    private static final ProfileDefinition PROFILE_TYPE = ProfileDefinition.NewUser;
    public static final String TEST_USER_CONFIG_VALUE = "TESTUSER";

    private Instant newUserPasswordPolicyCacheTime;
    private final Map<Locale, PwmPasswordPolicy> newUserPasswordPolicyCache = new HashMap<>();

    protected NewUserProfile( final DomainID domainID, final String identifier, final StoredConfiguration storedConfiguration )
    {
        super( domainID, identifier, storedConfiguration );
    }

    @Override
    public ProfileDefinition profileType( )
    {
        return PROFILE_TYPE;
    }

    @Override
    public String getDisplayName( final Locale locale )
    {
        final String value = this.readSettingAsLocalizedString( PwmSetting.NEWUSER_PROFILE_DISPLAY_NAME, locale );
        return value != null && !value.isEmpty() ? value : this.getIdentifier();
    }

    public PwmPasswordPolicy getNewUserPasswordPolicy( final PwmRequestContext pwmRequestContext )
            throws PwmUnrecoverableException
    {
        return getNewUserPasswordPolicy( pwmRequestContext.getSessionLabel(), pwmRequestContext.getPwmDomain(), pwmRequestContext.getLocale() );
    }

    public PwmPasswordPolicy getNewUserPasswordPolicy( final SessionLabel sessionLabel, final PwmDomain pwmDomain, final Locale locale )
            throws PwmUnrecoverableException
    {
        final DomainConfig domainConfig = pwmDomain.getConfig();
        final long maxNewUserCacheMS = Long.parseLong( domainConfig.getAppConfig().readAppProperty( AppProperty.CONFIG_NEWUSER_PASSWORD_POLICY_CACHE_MS ) );
        if ( newUserPasswordPolicyCacheTime != null && TimeDuration.fromCurrent( newUserPasswordPolicyCacheTime ).isLongerThan( maxNewUserCacheMS ) )
        {
            newUserPasswordPolicyCacheTime = Instant.now();
            newUserPasswordPolicyCache.clear();
        }

        final PwmPasswordPolicy cachedPolicy = newUserPasswordPolicyCache.get( locale );
        if ( cachedPolicy != null )
        {
            return cachedPolicy;
        }

        final PwmPasswordPolicy thePolicy;
        final LdapProfile ldapProfile = getLdapProfile( domainConfig );
        final String configuredNewUserPasswordDN = readSettingAsString( PwmSetting.NEWUSER_PASSWORD_POLICY_USER );
        if ( StringUtil.isEmpty( configuredNewUserPasswordDN ) )
        {
            final String errorMsg = "the setting "
                    + PwmSetting.NEWUSER_PASSWORD_POLICY_USER.toMenuLocationDebug( this.getIdentifier(), PwmConstants.DEFAULT_LOCALE )
                    + " must have a value";
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INVALID_CONFIG, errorMsg ) );
        }
        else
        {
            final String lookupDN;
            if ( TEST_USER_CONFIG_VALUE.equalsIgnoreCase( configuredNewUserPasswordDN ) )
            {
                lookupDN = ldapProfile.readSettingAsString( PwmSetting.LDAP_TEST_USER_DN );
                if ( StringUtil.isEmpty( lookupDN ) )
                {
                    final String errorMsg = "setting "
                            + PwmSetting.LDAP_TEST_USER_DN.toMenuLocationDebug( ldapProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE )
                            + " must be configured since setting "
                            + PwmSetting.NEWUSER_PASSWORD_POLICY_USER.toMenuLocationDebug( this.getIdentifier(), PwmConstants.DEFAULT_LOCALE )
                            + " is set to " + TEST_USER_CONFIG_VALUE;
                    throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INVALID_CONFIG, errorMsg ) );
                }
            }
            else
            {
                lookupDN = configuredNewUserPasswordDN;
            }

            if ( StringUtil.isEmpty( lookupDN ) )
            {
                throw new PwmUnrecoverableException( new ErrorInformation(
                        PwmError.ERROR_INVALID_CONFIG,
                        "user ldap dn in setting " + PwmSetting.NEWUSER_PASSWORD_POLICY_USER.toMenuLocationDebug(
                                null,
                                PwmConstants.DEFAULT_LOCALE ) + " can not be resolved" )
                );
            }
            else
            {
                try
                {
                    final ChaiProvider chaiProvider = pwmDomain.getProxyChaiProvider( sessionLabel, ldapProfile.getIdentifier() );
                    final ChaiUser chaiUser = chaiProvider.getEntryFactory().newChaiUser( lookupDN );
                    final UserIdentity userIdentity = UserIdentity.create( lookupDN, ldapProfile.getIdentifier(), pwmDomain.getDomainID() );
                    thePolicy = PasswordUtility.readPasswordPolicyForUser( pwmDomain, null, userIdentity, chaiUser );
                }
                catch ( final ChaiUnavailableException e )
                {
                    throw new PwmUnrecoverableException( PwmError.forChaiError( e.getErrorCode() ).orElse( PwmError.ERROR_INTERNAL ) );
                }
            }
        }
        newUserPasswordPolicyCache.put( locale, thePolicy );
        return thePolicy;
    }

    public TimeDuration getTokenDurationEmail( final DomainConfig domainConfig )
    {
        final long newUserDuration = readSettingAsLong( PwmSetting.NEWUSER_TOKEN_LIFETIME_EMAIL );
        if ( newUserDuration < 1 )
        {
            final long defaultDuration = domainConfig.readSettingAsLong( PwmSetting.TOKEN_LIFETIME );
            return TimeDuration.of( defaultDuration, TimeDuration.Unit.SECONDS );
        }
        return TimeDuration.of( newUserDuration, TimeDuration.Unit.SECONDS );
    }

    public TimeDuration getTokenDurationSMS( final DomainConfig domainConfig )
    {
        final long newUserDuration = readSettingAsLong( PwmSetting.NEWUSER_TOKEN_LIFETIME_SMS );
        if ( newUserDuration < 1 )
        {
            final long defaultDuration = domainConfig.readSettingAsLong( PwmSetting.TOKEN_LIFETIME );
            return TimeDuration.of( defaultDuration, TimeDuration.Unit.SECONDS );
        }
        return TimeDuration.of( newUserDuration, TimeDuration.Unit.SECONDS );
    }

    public static class NewUserProfileFactory implements ProfileFactory
    {
        @Override
        public Profile makeFromStoredConfiguration( final StoredConfiguration storedConfiguration, final DomainID domainID, final String identifier )
        {
            return new NewUserProfile( domainID, identifier, storedConfiguration );
        }
    }

    public LdapProfile getLdapProfile( final DomainConfig domainConfig )
            throws PwmUnrecoverableException
    {
        final String configuredProfile = readSettingAsString( PwmSetting.NEWUSER_LDAP_PROFILE );
        if ( StringUtil.notEmpty( configuredProfile ) )
        {
            final LdapProfile ldapProfile = domainConfig.getLdapProfiles().get( configuredProfile );
            if ( ldapProfile == null )
            {
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[]
                        {
                                "configured ldap profile for new user profile is invalid.  check setting "
                                        + PwmSetting.NEWUSER_LDAP_PROFILE.toMenuLocationDebug( this.getIdentifier(), PwmConstants.DEFAULT_LOCALE ),
                        }
                ) );
            }
            return ldapProfile;
        }
        return domainConfig.getDefaultLdapProfile();
    }
}
