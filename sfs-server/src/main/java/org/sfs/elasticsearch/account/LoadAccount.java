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

package org.sfs.elasticsearch.account;

import com.google.common.base.Optional;
import io.vertx.core.logging.Logger;
import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.sfs.Server;
import org.sfs.VertxContext;
import org.sfs.elasticsearch.Elasticsearch;
import org.sfs.elasticsearch.Jsonify;
import org.sfs.vo.PersistentAccount;
import rx.Observable;
import rx.functions.Func1;

import static com.google.common.base.Optional.absent;
import static com.google.common.base.Optional.of;
import static io.vertx.core.logging.LoggerFactory.getLogger;
import static java.lang.String.format;
import static org.sfs.vo.PersistentAccount.fromGetResponse;

public class LoadAccount implements Func1<String, Observable<Optional<PersistentAccount>>> {

    private static final Logger LOGGER = getLogger(LoadAccount.class);
    private final VertxContext<Server> vertxContext;

    public LoadAccount(VertxContext<Server> vertxContext) {
        this.vertxContext = vertxContext;
    }

    @Override
    public Observable<Optional<PersistentAccount>> call(final String accountId) {

        final Elasticsearch elasticSearch = vertxContext.verticle().elasticsearch();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(format("Get Request {%s,%s}", elasticSearch.defaultType(), elasticSearch.accountIndex(), accountId));
        }

        GetRequestBuilder request =
                elasticSearch.get()
                        .prepareGet(
                                elasticSearch.accountIndex(),
                                elasticSearch.defaultType(),
                                accountId);
        return elasticSearch.execute(vertxContext, request, elasticSearch.getDefaultGetTimeout())
                .map(oGetResponse -> {
                    GetResponse getResponse = oGetResponse.get();
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(format("Get Response {%s,%s} = %s", getResponse.getType(), elasticSearch.accountIndex(), accountId, Jsonify.toString(getResponse)));
                    }
                    if (getResponse.isExists()) {
                        return of(fromGetResponse(getResponse));
                    } else {
                        return absent();
                    }
                });
    }
}
