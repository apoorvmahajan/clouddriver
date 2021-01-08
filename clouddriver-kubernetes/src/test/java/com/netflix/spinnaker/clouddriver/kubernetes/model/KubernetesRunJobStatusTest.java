/*
 * Copyright 2020 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Yaml;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class KubernetesRunJobStatusTest {

  private String getResource(String name) {
    try {
      return Resources.toString(
          KubernetesRunJobStatusTest.class.getResource(name), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private KubernetesJobStatus getKubernetesJobStatus(String manifestPath) {
    KubernetesManifest testManifest =
        Yaml.loadAs(getResource("base.yml"), KubernetesManifest.class);

    KubernetesManifest overlay = Yaml.loadAs(getResource(manifestPath), KubernetesManifest.class);
    testManifest.putAll(overlay);

    V1Job job = KubernetesCacheDataConverter.getResource(testManifest, V1Job.class);
    KubernetesJobStatus kubernetesJobStatus = new KubernetesJobStatus(job, "mock-account");

    List<KubernetesManifest> pods = ImmutableList.of(testManifest);
    kubernetesJobStatus.setPods(
        pods.stream()
            .map(
                p -> {
                  V1Pod pod = KubernetesCacheDataConverter.getResource(p, V1Pod.class);
                  return new KubernetesJobStatus.PodStatus(pod);
                })
            .collect(Collectors.toList()));
    return kubernetesJobStatus;
  }

  @Test
  void testCompletionDetailsForFailedAppContainer() {
    KubernetesJobStatus kubernetesJobStatus = getKubernetesJobStatus("failed-job.yml");
    Map<String, String> result = kubernetesJobStatus.getCompletionDetails();
    assertFalse(result.isEmpty());
    assertThat(result.get("message"))
        .isEqualTo(
            "Failed to download the file: foo.\n"
                + "GET Request failed with status code', 404, 'Expected', <HTTPStatus.OK: 200>)\n");
    assertThat(result.get("exitCode")).isEqualTo("1");
    assertThat(result.get("signal")).isEmpty();
    assertThat(result.get("reason")).isEqualTo("Error");
  }

  @Test
  void testCompletionDetailsForFailedInitContainer() {
    KubernetesJobStatus kubernetesJobStatus =
        getKubernetesJobStatus("failed-job-init-container-error.yml");
    Map<String, String> result = kubernetesJobStatus.getCompletionDetails();
    assertFalse(result.isEmpty());
    assertThat(result.get("message")).isEqualTo("foo");
    assertThat(result.get("exitCode")).isEqualTo("1");
    assertThat(result.get("signal")).isEmpty();
    assertThat(result.get("reason")).isEqualTo("Error");
  }

  @Test
  void testCompletionDetailsForSuccessfulJobCompletion() {
    KubernetesJobStatus kubernetesJobStatus = getKubernetesJobStatus("successful-job.yml");
    Map<String, String> result = kubernetesJobStatus.getCompletionDetails();
    assertFalse(result.isEmpty());
    assertThat(result.get("message")).isNullOrEmpty();
    assertThat(result.get("exitCode")).isNullOrEmpty();
    assertThat(result.get("signal")).isNullOrEmpty();
    assertThat(result.get("reason")).isNullOrEmpty();
  }
}
