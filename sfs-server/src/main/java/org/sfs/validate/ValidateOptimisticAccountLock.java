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

package org.sfs.validate;

import com.google.common.base.Optional;
import io.vertx.core.json.JsonObject;
import org.sfs.util.HttpRequestValidationException;
import org.sfs.vo.PersistentAccount;
import rx.functions.Func1;

import static java.lang.String.format;
import static java.net.HttpURLConnection.HTTP_CONFLICT;

public class ValidateOptimisticAccountLock implements Func1<Optional<PersistentAccount>, PersistentAccount> {

    @Override
    public PersistentAccount call(Optional<PersistentAccount> subInput) {
        if (!subInput.isPresent()) {
            JsonObject jsonObject = new JsonObject()
                    .put("message", format("Account was modified concurrently. Please retry the request."));
            throw new HttpRequestValidationException(HTTP_CONFLICT, jsonObject);
        }
        return subInput.get();
    }
}
