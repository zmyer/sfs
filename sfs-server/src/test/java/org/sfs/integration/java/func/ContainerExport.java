/*
 * Copyright 2016 The Simple File Server Authors
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

package org.sfs.integration.java.func;

import com.google.common.collect.ListMultimap;
import com.google.common.escape.Escaper;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.logging.Logger;
import org.sfs.rx.HttpClientKeepAliveResponseBodyBuffer;
import org.sfs.rx.ObservableFuture;
import org.sfs.rx.RxHelper;
import rx.Observable;
import rx.functions.Func1;

import java.nio.file.Path;

import static com.google.common.base.Joiner.on;
import static com.google.common.collect.ArrayListMultimap.create;
import static com.google.common.collect.FluentIterable.from;
import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.google.common.net.UrlEscapers.urlFormParameterEscaper;
import static io.vertx.core.logging.LoggerFactory.getLogger;
import static org.sfs.integration.java.help.AuthorizationFactory.Producer;
import static org.sfs.rx.Defer.just;
import static org.sfs.util.SfsHttpHeaders.X_SFS_DEST_DIRECTORY;

public class ContainerExport implements Func1<Void, Observable<HttpClientResponseAndBuffer>> {

    private static final Logger LOGGER = getLogger(ContainerExport.class);
    private final HttpClient httpClient;
    private final String accountName;
    private final String containerName;
    private final Producer auth;
    private ListMultimap<String, String> queryParams = create();
    private ListMultimap<String, String> headerParams = create();
    private final Path destDirectory;

    public ContainerExport(HttpClient httpClient, String accountName, String containerName, Path destDirectory, Producer auth) {
        this.httpClient = httpClient;
        this.accountName = accountName;
        this.containerName = containerName;
        this.auth = auth;
        this.destDirectory = destDirectory;
    }

    public ContainerExport setHeader(String name, String value) {
        headerParams.removeAll(name);
        headerParams.put(name, value);
        return this;
    }

    public ContainerExport setHeader(String name, String value, String... values) {
        headerParams.removeAll(name);
        headerParams.put(name, value);
        for (String v : values) {
            headerParams.put(name, v);
        }
        return this;
    }

    public ContainerExport setQueryParam(String name, String value) {
        queryParams.removeAll(name);
        queryParams.put(name, value);
        return this;
    }

    public ContainerExport setQueryParam(String name, String value, String... values) {
        queryParams.removeAll(name);
        queryParams.put(name, value);
        for (String v : values) {
            queryParams.put(name, v);
        }
        return this;
    }

    @Override
    public Observable<HttpClientResponseAndBuffer> call(Void aVoid) {
        return auth.toHttpAuthorization()
                .flatMap(s -> {
                    final Escaper escaper = urlFormParameterEscaper();

                    Iterable<String> keyValues = from(queryParams.entries())
                            .transform(input -> escaper.escape(input.getKey()) + '=' + escaper.escape(input.getValue()));

                    String query = on('&').join(keyValues);

                    ObservableFuture<HttpClientResponse> handler = RxHelper.observableFuture();
                    HttpClientRequest httpClientRequest =
                            httpClient.post("/export_container/" + accountName + "/" + containerName + (query.length() > 0 ? "?" + query : ""), handler::complete)
                                    .exceptionHandler(handler::fail)
                                    .setTimeout(20000)
                                    .putHeader(AUTHORIZATION, s);
                    for (String entry : headerParams.keySet()) {
                        httpClientRequest = httpClientRequest.putHeader(entry, headerParams.get(entry));
                    }
                    httpClientRequest = httpClientRequest.putHeader(X_SFS_DEST_DIRECTORY, destDirectory.toString());
                    httpClientRequest.end();
                    return handler
                            .flatMap(httpClientResponse ->
                                    just(httpClientResponse)
                                            .flatMap(new HttpClientKeepAliveResponseBodyBuffer())
                                            .map(buffer -> new HttpClientResponseAndBuffer(httpClientResponse, buffer)))
                            .single();
                });
    }
}
