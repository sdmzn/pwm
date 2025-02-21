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

package password.pwm.http.bean;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import password.pwm.bean.TokenDestinationItem;
import password.pwm.bean.UserIdentity;
import password.pwm.config.option.SessionBeanMode;

import java.util.Collections;
import java.util.Set;

@Data
@EqualsAndHashCode( callSuper = false )
public class ActivateUserBean extends PwmSessionBean
{
    @SerializedName( "tp" )
    private boolean tokenPassed;

    @SerializedName( "ap" )
    private boolean agreementPassed;

    @SerializedName( "v" )
    private boolean formValidated;

    @SerializedName( "u" )
    private UserIdentity userIdentity;

    @SerializedName( "ts" )
    private boolean tokenSent;

    @SerializedName( "td" )
    private TokenDestinationItem tokenDestination;

    @SerializedName( "p" )
    private String profileID;


    @Override
    public BeanType getBeanType( )
    {
        return BeanType.PUBLIC;
    }

    @Override
    public Set<SessionBeanMode> supportedModes( )
    {
        return Collections.singleton( SessionBeanMode.LOCAL );
    }
}
