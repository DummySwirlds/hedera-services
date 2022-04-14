package com.hedera.services.bdd.suites.contract.opcodes;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.crypto.Hash;
import org.junit.jupiter.api.Assertions;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;

public class ExtCodeHashOperationSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ExtCodeHashOperationSuite.class);

	public static void main(String[] args) {
		new ExtCodeHashOperationSuite().runSuiteAsync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[]{
				verifiesExistence()
		});
	}

	HapiApiSpec verifiesExistence() {
		final var contract = "ExtCodeOperationsChecker";
		final var INVALID_ADDRESS = "0x0000000000000000000000000000000000123456";
		final var EXPECTED_ACCOUNT_HASH = ByteString.copyFrom(Hash.keccak256(Bytes.EMPTY).toArray());

		return defaultHapiSpec("VerifiesExistence")
				.given(
						uploadInitCode(contract),
						contractCreate(contract)
				).when(
				)
				.then(
						contractCall(contract, "hashOf", INVALID_ADDRESS)
								.hasKnownStatus(INVALID_SOLIDITY_ADDRESS),
						contractCallLocal(contract, "hashOf", INVALID_ADDRESS)
								.hasAnswerOnlyPrecheck(INVALID_SOLIDITY_ADDRESS),
						withOpContext((spec, opLog) -> {
							final var accountID = spec.registry().getAccountID(DEFAULT_PAYER);
							final var contractID = spec.registry().getContractId(contract);
							final var accountSolidityAddress = asHexedSolidityAddress(accountID);
							final var contractAddress = asHexedSolidityAddress(contractID);

							final var call = contractCall(contract, "hashOf",
									accountSolidityAddress)
									.via("callRecord");
							final var callRecord = getTxnRecord("callRecord");

							final var accountCodeHashCallLocal = contractCallLocal(contract, "hashOf",
									accountSolidityAddress)
									.saveResultTo("accountCodeHash");

							final var contractCodeHash = contractCallLocal(contract, "hashOf",
									contractAddress)
									.saveResultTo("contractCodeHash");

							final var getBytecode = getContractBytecode(contract)
									.saveResultTo("contractBytecode");

							allRunFor(spec, call, callRecord, accountCodeHashCallLocal, contractCodeHash, getBytecode);

							final var recordResult = callRecord.getResponseRecord().getContractCallResult();
							final var accountCodeHash = spec.registry().getBytes("accountCodeHash");

							final var contractCodeResult = spec.registry().getBytes("contractCodeHash");
							final var contractBytecode = spec.registry().getBytes("contractBytecode");
							final var expectedContractCodeHash =
									ByteString.copyFrom(Hash.keccak256(Bytes.of(contractBytecode)).toArray()).toByteArray();

							Assertions.assertEquals(EXPECTED_ACCOUNT_HASH, recordResult.getContractCallResult());
							Assertions.assertArrayEquals(EXPECTED_ACCOUNT_HASH.toByteArray(), accountCodeHash);
							Assertions.assertArrayEquals(expectedContractCodeHash, contractCodeResult);
						})
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
