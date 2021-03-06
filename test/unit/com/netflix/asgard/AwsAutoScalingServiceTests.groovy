/*
 * Copyright 2012 Netflix, Inc.
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
package com.netflix.asgard

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.Instance
import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.amazonaws.services.ec2.model.AvailabilityZone
import com.netflix.asgard.mock.Mocks
import com.netflix.asgard.push.Cluster

class AwsAutoScalingServiceTests extends GroovyTestCase {

    UserContext userContext = Mocks.userContext()

    void setUp() {
        Mocks.createDynamicMethods() 
    }

    void testGetClusters() {
        AwsAutoScalingService asgService = Mocks.awsAutoScalingService()
        Collection<Cluster> clusters = asgService.getClusters(userContext)
        assert clusters.size() > 1
        assert clusters.any { 'akms' == it.name}
    }

    void testGetCluster() {
        AwsAutoScalingService asgService = Mocks.awsAutoScalingService()
        Cluster cluster = asgService.getCluster(userContext, 'akms')
        assert 1 == cluster.size()
        assert 'akms' == cluster.name
        assert 'akms-v002' in cluster.groups*.autoScalingGroupName
    }

    void testGetAutoScalingGroupForNullInstanceId() {
        AwsAutoScalingService asgService = Mocks.awsAutoScalingService()
        assertNull asgService.getAutoScalingGroupFor(userContext, null)
    }

    void testGetAutoScalingGroupForInvalidInstanceId() {
        AwsAutoScalingService asgService = Mocks.awsAutoScalingService()
        assertNull asgService.getAutoScalingGroupFor(userContext, "invalid")
    }

    void testGetAutoScalingGroupForNonExistentInstanceId() {
        AwsAutoScalingService asgService = Mocks.awsAutoScalingService()
        assertNull asgService.getAutoScalingGroupFor(userContext, "i-4ef26823")
    }

    void testGetAutoScalingGroupForInstanceId() {
        AwsAutoScalingService asgService = Mocks.awsAutoScalingService()
        assert 'helloworld-example-v015' == asgService.getAutoScalingGroupFor(userContext, "i-8ee4eeee").autoScalingGroupName
    }

    void testGetLaunchConfigurationNullName() {
        AwsAutoScalingService asgService = Mocks.awsAutoScalingService()
        assertNull asgService.getLaunchConfiguration(userContext, null)
    }

    void testGetLaunchConfigurationInvalidName() {
        AwsAutoScalingService asgService = Mocks.awsAutoScalingService()
        assertNull asgService.getLaunchConfiguration(userContext, "invalid/nonsense")
    }

    void testGetLaunchConfigurationNonExistentName() {
        AwsAutoScalingService asgService = Mocks.awsAutoScalingService()
        assertNull asgService.getLaunchConfiguration(userContext, "helloworld-doesntexist")
    }

    void testGetLaunchConfigurationName() {
        AwsAutoScalingService asgService = Mocks.awsAutoScalingService()
        assert 'helloworld-example-v015-20111014165240' == asgService.getLaunchConfiguration(userContext,
                'helloworld-example-v015-20111014165240').launchConfigurationName
    }

    void testGetAutoScalingGroupNonExistent() {
        AwsAutoScalingService asgService = Mocks.awsAutoScalingService()
        assertNull asgService.getAutoScalingGroup(userContext, "doesn't exist")
    }

    void testLaunchConfigurationGetUserDataTruncated() {
        String typicalUserData = '#!/bin/bash\n' +
                'set -x\n' +
                '\n' +
                'PATH=/bin:/usr/bin:/usr/sbin:/sbin\n' +
                '\n' +
                'app="abcache"\n' +
                'appenv="test"\n' +
                'appuser="${app}${appenv}"\n' +
                'autogrp="abcache"\n' +
                'setenv_sh="/apps/tomcat/bin/setenv.sh"\n' +
                'NFenv="/apps/tomcat/bin/netflix.env"\n' +
                'server_template_xml="/apps/tomcat/conf/server_template.xml"\n' +
                'server_xml="/apps/tomcat/conf/server.xml"\n' +
                'instanceid=`/usr/bin/instance-id`\n' +
                'appdynappname="Cloud Milestones - $appenv AWS"\n' +
                'cf1=/apps/appagent/conf/controller-info-template.xml\n' +
                'cf2=/apps/machineagent/conf/controller-info-template.xml\n' +
                'cf3=/apps/machineagent/monitors/JMXPollMonitor/monitor-template.xml\n' +
                'cf4=/apps/nimbus/robot/robot-template.cfg\n' +
                'cf5=/apps/machineagent/monitors/ApacheErrors/monitor-template.xml\n' +
                'appdynappname="Cloud Milestones - test AWS'

        String unusualUserData = "require 'open-uri'\n" +
                "require 'socket'\n" +
                "require 'rexml/document'\n" +
                "require 'ftools'\n" +
                "require 'fileutils'\n" +
                "\n" +
                'env="test"' +
                'controller_ip =  "ec2-174-129-234-182.compute-1.amazonaws.com"\n' +
                'app_name = "Cloud Milestones - #{env} AWS"\n' +
                'tier_name = "PlayReady DRM"\n' +
                'paths=["c:/apps/appagent/conf/controller-info-template.xml", "c:/apps/machineagent/conf/controller-info-template.xml"]\n' +
                '\n' +
                'def get_instance_id\n' +
                "    return  open('http://169.254.169.254/latest/meta-data/instance-id').read\n" +
                'end\n' +
                '\n' +
                'def get_hostname\n' +
                '    return Socket.gethostname\n' +
                'end\n' +
                '\n' +
                '# Keep polling hostname until the hostname is updated or \n' +
                '# this function times out. We need to do this because a new Windows\n' +
                '# EC2 instance will have its hostname updated during its first startup, \n' +
                "# and we don't know when this process finishes. \n" +
                'def pending_hostname(timeout=600) \n' +
                '    hostname = get_hostname\n' +
                '    new_hostname = hostname\n' +
                '    puts "initial hostname: #{hostname}"\n' +
                '    elapsed = 0\n' +
                "    interval = 2'\n"

        Mocks.awsAutoScalingService()

        LaunchConfiguration configTypical = new LaunchConfiguration(userData: typicalUserData)
        assertEquals '...app="abcache"\n' +
                'appenv="test"\n' +
                'appuser="${app}${appenv}"\n' +
                'autogrp="abcache"...', configTypical.userDataTruncated

        LaunchConfiguration configUnusual = new LaunchConfiguration(userData: unusualUserData)
        assertEquals "require 'open-uri'\n" +
                "require 'socket'\n" +
                "require 'rexml/document'\n" +
                "require 'ftools'\n" +
                "require 'fileutils'\n" +
                "\n" +
                'env="test"' +
                'controller_ip =  "ec2-174-129-234-182.compute-1.amazonaws.com"\n' +
                'app_name = "Cloud Milesto...', configUnusual.userDataTruncated
    }

    void testAutoScalingGroupGetAppName() {
        Mocks.awsAutoScalingService()
        assert "actiondrainer" == new AutoScalingGroup().withAutoScalingGroupName("actiondrainer").appName
        assert "actiondrainer" == new AutoScalingGroup().withAutoScalingGroupName("actiondrainer").getAppName()
        assert "merchweb" == new AutoScalingGroup().withAutoScalingGroupName("merchweb--loadtest").appName
        assert "discovery" == new AutoScalingGroup().withAutoScalingGroupName("discovery--us-east-1d").appName
        assert "discovery" == new AutoScalingGroup().withAutoScalingGroupName("discovery-us-east-1d").appName
        assert "merchweb" == new AutoScalingGroup().withAutoScalingGroupName("merchweb-loadtest").appName
        assert "merchweb" == new AutoScalingGroup().withAutoScalingGroupName("merchweb-loadtest").getAppName()
        assert "api" == new AutoScalingGroup().withAutoScalingGroupName("api-test-A").appName
        assert "evcache" == new AutoScalingGroup().withAutoScalingGroupName("evcache-us-east-1d-0").appName
        assert "evcache" == new AutoScalingGroup().withAutoScalingGroupName("evcache-us-east-1d-0").getAppName()
    }

    void testAsgInstanceCopy() {
        Mocks.awsAutoScalingService()

        // Copy duplicates the fields into a new object.
        Instance asgInstance = new Instance().withInstanceId('i-deadbeef')
        Instance asgInstanceCopy = asgInstance.copy()
        assert asgInstance.instanceId == asgInstanceCopy.instanceId
        assert !asgInstance.is(asgInstanceCopy)

        // Changing the original doesn't change the copy.
        asgInstance.instanceId = 'i-12345678'
        assert asgInstanceCopy.instanceId == 'i-deadbeef'
    }

    void testAsgCopy() {
        Mocks.awsAutoScalingService()

        Instance instanceAbcd = new Instance().withInstanceId('i-abcdabcd')
        Instance instanceEfff = new Instance().withInstanceId('i-efffefff')

        // Copy duplicates the fields into a new object.
        AutoScalingGroup autoScalingGroup = new AutoScalingGroup().withAutoScalingGroupName('marypoppins').
                withInstances([instanceAbcd, instanceEfff])
        AutoScalingGroup asgCopy = autoScalingGroup.copy()

        Instance abcdCopy = asgCopy.instances[0]
        Instance efffCopy = asgCopy.instances[1]

        assert !asgCopy.is(autoScalingGroup)
        assert asgCopy.autoScalingGroupName == autoScalingGroup.autoScalingGroupName

        assert !abcdCopy.is(instanceAbcd)
        assert instanceAbcd.instanceId == 'i-abcdabcd'
        assert abcdCopy.instanceId == 'i-abcdabcd'

        assert !efffCopy.is(instanceEfff)
        assert instanceEfff.instanceId == 'i-efffefff'
        assert efffCopy.instanceId == 'i-efffefff'
    }

    void testAutoScalingGroupGetStack() {
        Mocks.awsAutoScalingService()
        assert "" == new AutoScalingGroup().withAutoScalingGroupName("actiondrainer").stack
        assert "" == new AutoScalingGroup().withAutoScalingGroupName("actiondrainer").getStack()
        assert "" == new AutoScalingGroup().withAutoScalingGroupName("merchweb--loadtest").stack
        assert "" == new AutoScalingGroup().withAutoScalingGroupName("discovery--us-east-1d").stack
        assert "us" == new AutoScalingGroup().withAutoScalingGroupName("discovery-us-east-1d").stack
        assert "loadtest" == new AutoScalingGroup().withAutoScalingGroupName("merchweb-loadtest").stack
        assert "loadtest" == new AutoScalingGroup().withAutoScalingGroupName("merchweb-loadtest").getStack()
        assert "test" == new AutoScalingGroup().withAutoScalingGroupName("api-test-A").stack
        assert "us" == new AutoScalingGroup().withAutoScalingGroupName("evcache-us-east-1d-0").stack
        assert "us" == new AutoScalingGroup().withAutoScalingGroupName("evcache-us-east-1d-0").getStack()
    }

    void testAutoScalingGroupGetClusterName() {
        Mocks.awsAutoScalingService()
        assert "actiondrainer" == new AutoScalingGroup().withAutoScalingGroupName("actiondrainer").clusterName
        assert "actiondrainer" == new AutoScalingGroup().withAutoScalingGroupName("actiondrainer").getClusterName()
        assert "merchweb--loadtest" == new AutoScalingGroup().withAutoScalingGroupName("merchweb--loadtest").clusterName
        assert "discovery--us-east-1d" == new AutoScalingGroup().withAutoScalingGroupName("discovery--us-east-1d").clusterName
        assert "discovery-us-east-1d" == new AutoScalingGroup().withAutoScalingGroupName("discovery-us-east-1d").clusterName
        assert "merchweb-loadtest" == new AutoScalingGroup().withAutoScalingGroupName("merchweb-loadtest").clusterName
        assert "merchweb-loadtest" == new AutoScalingGroup().withAutoScalingGroupName("merchweb-loadtest-v001").getClusterName()
        assert "api-test-A" == new AutoScalingGroup().withAutoScalingGroupName("api-test-A-v304").clusterName
        assert "evcache-us-east-1d-0" == new AutoScalingGroup().withAutoScalingGroupName("evcache-us-east-1d-0").clusterName
        assert "evcache-us-east-1d-0" == new AutoScalingGroup().withAutoScalingGroupName("evcache-us-east-1d-0").getClusterName()
    }

    void testAvailabilityZoneShouldBePreselected() {
        Mocks.awsAutoScalingService()
        assert new AvailabilityZone().withZoneName("us-east-1a").shouldBePreselected(null, null)
        assert !new AvailabilityZone().withZoneName("us-east-1b").shouldBePreselected(null, null)
        assert new AvailabilityZone().withZoneName("us-east-1c").shouldBePreselected(null, null)
        assert new AvailabilityZone().withZoneName("us-east-1d").shouldBePreselected(null, null)
        assert new AvailabilityZone().withZoneName("us-east-1d").shouldBePreselected(null, null)

        assert new AvailabilityZone().withZoneName("us-east-1b").shouldBePreselected(['us-east-1b', 'us-east-1c'], null)
        assert new AvailabilityZone().withZoneName("us-east-1b").shouldBePreselected(['us-east-1b'], null)
        assert !new AvailabilityZone().withZoneName("us-east-1b").shouldBePreselected(['us-east-1c'], null)
        assert !new AvailabilityZone().withZoneName("us-east-1b").shouldBePreselected('us-east-1c', null)
        assert new AvailabilityZone().withZoneName("us-east-1b").shouldBePreselected('us-east-1b', null)

        assert !new AvailabilityZone().withZoneName("us-east-1b").shouldBePreselected(null,
                new AutoScalingGroup().withAvailabilityZones())
        assert new AvailabilityZone().withZoneName("us-east-1b").shouldBePreselected(null,
                new AutoScalingGroup().withAvailabilityZones('us-east-1b'))
        assert new AvailabilityZone().withZoneName("us-east-1b").shouldBePreselected(null,
                new AutoScalingGroup().withAvailabilityZones('us-east-1b', 'us-east-1c'))
    }

}
