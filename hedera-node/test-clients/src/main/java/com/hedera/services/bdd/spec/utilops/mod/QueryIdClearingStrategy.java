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

package com.hedera.services.bdd.spec.utilops.mod;

import static com.hedera.services.bdd.spec.utilops.mod.ModificationUtils.withClearedField;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;

public class QueryIdClearingStrategy extends IdClearingStrategy<QueryModification>
        implements QueryModificationStrategy {
    private static final Map<String, ExpectedAnswer> CLEARED_ID_ANSWERS = Map.ofEntries(
            Map.entry("proto.ContractGetInfoQuery.contractID", ExpectedAnswer.onAnswerOnly(INVALID_CONTRACT_ID)),
            Map.entry("proto.ContractCallLocalQuery.contractID", ExpectedAnswer.onAnswerOnly(INVALID_CONTRACT_ID)),
            Map.entry("proto.CryptoGetAccountBalanceQuery.accountID", ExpectedAnswer.onAnswerOnly(INVALID_ACCOUNT_ID)),
            Map.entry("proto.CryptoGetAccountRecordsQuery.accountID", ExpectedAnswer.onAnswerOnly(INVALID_ACCOUNT_ID)),
            Map.entry("proto.CryptoGetInfoQuery.accountID", ExpectedAnswer.onAnswerOnly(INVALID_ACCOUNT_ID)),
            Map.entry("proto.ContractGetBytecodeQuery.contractID", ExpectedAnswer.onAnswerOnly(INVALID_CONTRACT_ID)),
            Map.entry("proto.FileGetContentsQuery.fileID", ExpectedAnswer.onAnswerOnly(INVALID_FILE_ID)),
            Map.entry("proto.FileGetInfoQuery.fileID", ExpectedAnswer.onAnswerOnly(INVALID_FILE_ID)),
            Map.entry(
                    "proto.TransactionID.accountID",
                    ExpectedAnswer.onAnswerOnly(INVALID_ACCOUNT_ID, INVALID_TRANSACTION_ID)),
            Map.entry("proto.ConsensusGetTopicInfoQuery.topicID", ExpectedAnswer.onAnswerOnly(INVALID_TOPIC_ID)),
            Map.entry("proto.TokenGetInfoQuery.token", ExpectedAnswer.onAnswerOnly(INVALID_TOKEN_ID)),
            Map.entry("proto.ScheduleGetInfoQuery.scheduleID", ExpectedAnswer.onAnswerOnly(INVALID_SCHEDULE_ID)),
            Map.entry("proto.NftID.token_ID", ExpectedAnswer.onAnswerOnly(INVALID_TOKEN_ID)),
            Map.entry("proto.GetAccountDetailsQuery.account_id", ExpectedAnswer.onAnswerOnly(INVALID_ACCOUNT_ID)));

    @NonNull
    @Override
    public QueryModification modificationForTarget(@NonNull final TargetField targetField, final int encounterIndex) {
        final var expectedAnswer = CLEARED_ID_ANSWERS.get(targetField.name());
        requireNonNull(expectedAnswer, "No expected answer for field " + targetField.name());
        return new QueryModification(
                "Clearing field " + targetField.name() + " (#" + encounterIndex + ")",
                QueryMutation.withTransform(q -> withClearedField(q, targetField.descriptor(), encounterIndex)),
                expectedAnswer);
    }
}