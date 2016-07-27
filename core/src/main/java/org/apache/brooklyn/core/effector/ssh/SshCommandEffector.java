/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.core.effector.ssh;

import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.Maps;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.effector.ParameterType;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.config.MapConfigKey;
import org.apache.brooklyn.core.effector.AddEffector;
import org.apache.brooklyn.core.effector.EffectorBody;
import org.apache.brooklyn.core.effector.Effectors;
import org.apache.brooklyn.core.effector.Effectors.EffectorBuilder;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.sensor.ssh.SshCommandSensor;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.json.ShellEnvironmentSerializer;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.exceptions.Exceptions;

public final class SshCommandEffector extends AddEffector {

    public static final ConfigKey<String> EFFECTOR_COMMAND = ConfigKeys.newStringConfigKey("command");
    public static final ConfigKey<String> EFFECTOR_EXECUTION_DIR = SshCommandSensor.SENSOR_EXECUTION_DIR;
    public static final MapConfigKey<Object> EFFECTOR_SHELL_ENVIRONMENT = BrooklynConfigKeys.SHELL_ENVIRONMENT;

    public SshCommandEffector(ConfigBag params) {
        super(newEffectorBuilder(params).build());
    }

    public SshCommandEffector(Map<String,String> params) {
        this(ConfigBag.newInstance(params));
    }

    public static EffectorBuilder<String> newEffectorBuilder(ConfigBag params) {
        EffectorBuilder<String> eff = AddEffector.newEffectorBuilder(String.class, params);
        eff.impl(new Body(eff.buildAbstract(), params));
        return eff;
    }

    protected static class Body extends EffectorBody<String> {
        private final Effector<?> effector;
        private final String command;
        private final String executionDir;

        public Body(Effector<?> eff, ConfigBag params) {
            this.effector = eff;
            this.command = Preconditions.checkNotNull(params.get(EFFECTOR_COMMAND), "SSH command must be supplied when defining this effector");
            this.executionDir = params.get(EFFECTOR_EXECUTION_DIR);
        }

        @Override
        public String call(ConfigBag params) {
            String sshCommand = SshCommandSensor.makeCommandExecutingInDirectory(command, executionDir, entity());

            MutableMap<String, Object> env = MutableMap.of();

            // Set all declared parameters, including default values
            for (ParameterType<?> param : effector.getParameters()) {
                env.addIfNotNull(param.getName(), params.get(Effectors.asConfigKey(param)));
            }

            // Set things from the entities defined shell environment, if applicable
            env.putAll(entity().config().get(BrooklynConfigKeys.SHELL_ENVIRONMENT));

            // Add the shell environment entries from our configuration
            Map<String, Object> effectorEnv = params.get(EFFECTOR_SHELL_ENVIRONMENT);
            if (effectorEnv != null) env.putAll(effectorEnv);

            // Set the parameters we've been passed. This will repeat declared parameters but to no harm,
            // it may pick up additional values (could be a flag defining whether this is permitted or not.)
            // Make sure we do not include the shell.env here again, by filtering it out.
            env.putAll(Maps.filterKeys(params.getAllConfig(), Predicates.not(Predicates.equalTo(EFFECTOR_SHELL_ENVIRONMENT.getName()))));

            // Try to resolve the configuration in the env Map
            try {
                env = (MutableMap<String, Object>) Tasks.resolveDeepValue(env, Object.class, entity().getExecutionContext());
            } catch (InterruptedException | ExecutionException e) {
                Exceptions.propagateIfFatal(e);
            }

            // Execute the effector with the serialized environment strings
            ShellEnvironmentSerializer serializer = new ShellEnvironmentSerializer(entity().getManagementContext());
            SshEffectorTasks.SshEffectorTaskFactory<String> task = SshEffectorTasks.ssh(sshCommand)
                    .requiringZeroAndReturningStdout()
                    .summary("effector "+effector.getName())
                    .environmentVariables(serializer.serialize(env));

            return queue(task).get();
        }
    }
}
