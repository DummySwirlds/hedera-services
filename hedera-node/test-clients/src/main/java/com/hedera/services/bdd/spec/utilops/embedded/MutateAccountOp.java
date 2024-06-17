/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.bdd.spec.utilops.embedded;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Consumer;

public class MutateAccountOp extends UtilOp {
    private final String account;
    private final Consumer<Account.Builder> mutation;

    public MutateAccountOp(String account, Consumer<Account.Builder> mutation) {
        this.account = account;
        this.mutation = mutation;
    }

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        final var accounts = spec.embeddedAccountsOrThrow();
        final var targetId = PbjConverter.toPbj(TxnUtils.asId(account, spec));
        final var account = requireNonNull(accounts.get(targetId));
        final var builder = account.copyBuilder();
        mutation.accept(builder);
        accounts.put(targetId, builder.build());
        spec.commitEmbeddedState();
        return false;
    }
}