package com.hedera.services.bdd.spec.assertions;

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
import com.hedera.services.bdd.spec.queries.contract.HapiGetContractInfo;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.contract.Utils;
import com.hedera.services.bdd.suites.utils.contracts.ContractCallResult;
import com.hedera.services.bdd.spec.utilops.UtilStateChange;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.swirlds.common.CommonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.core.CallTransaction;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;

public class ContractFnResultAsserts extends BaseErroringAssertsProvider<ContractFunctionResult> {
	static final Logger log = LogManager.getLogger(ContractFnResultAsserts.class);

	Optional<String> resultAbi = Optional.empty();
	Optional<Function<HapiApiSpec, Function<Object[], Optional<Throwable>>>> objArrayAssert = Optional.empty();

	public static ContractFnResultAsserts resultWith() {
		return new ContractFnResultAsserts();
	}

	public ContractFnResultAsserts resultThruAbi(
			String abi, Function<HapiApiSpec, Function<Object[], Optional<Throwable>>> provider) {
		registerProvider((spec, o) -> {
			Object[] actualObjs = viaAbi(abi, ((ContractFunctionResult) o).getContractCallResult().toByteArray());
			Optional<Throwable> error = provider.apply(spec).apply(actualObjs);
			if (error.isPresent()) {
				throw error.get();
			}
		});
		return this;
	}

	/*  Note:
	    This method utilizes algorithmic extraction of a function ABI by the name of the function and the contract
	    and should replace the "resultThruAbi" method, which depends on function ABI, passed as String literal.
    */
	public ContractFnResultAsserts resultViaFunctionName(final String functionName,
														 final String contractName,
														 final Function<HapiApiSpec, Function<Object[], Optional<Throwable>>> provider) {
		final var abi = Utils.getABIFor(FUNCTION, functionName, contractName);
		registerProvider((spec, o) -> {
			Object[] actualObjs = viaAbi(abi, ((ContractFunctionResult) o).getContractCallResult().toByteArray());
			Optional<Throwable> error = provider.apply(spec).apply(actualObjs);
			if (error.isPresent()) {
				throw error.get();
			}
		});
		return this;
	}

	public static Object[] viaAbi(String abi, byte[] bytes) {
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(abi);
		return function.decodeResult(bytes);
	}

	public ContractFnResultAsserts contract(String contract) {
		registerIdLookupAssert(contract, r -> r.getContractID(), ContractID.class, "Bad contract!");
		return this;
	}

	public ContractFnResultAsserts hexedEvmAddress(String expected) {
		return evmAddress(ByteString.copyFrom(CommonUtils.unhex(expected)));
	}

	public ContractFnResultAsserts evmAddress(ByteString expected) {
		registerProvider((spec, o) -> {
			final var result = (ContractFunctionResult) o;
			Assertions.assertTrue(result.hasEvmAddress(), "Missing EVM address, expected " + expected);
			final var actual = result.getEvmAddress().getValue();
			Assertions.assertEquals(expected, actual, "Bad EVM address");
		});
		return this;
	}

	public ContractFnResultAsserts logs(ErroringAssertsProvider<List<ContractLoginfo>> provider) {
		registerProvider((spec, o) -> {
			List<ContractLoginfo> logs = ((ContractFunctionResult) o).getLogInfoList();
			ErroringAsserts<List<ContractLoginfo>> asserts = provider.assertsFor(spec);
			List<Throwable> errors = asserts.errorsIn(logs);
			AssertUtils.rethrowSummaryError(log, "Bad logs!", errors);
		});
		return this;
	}

	public ContractFnResultAsserts error(String msg) {
		registerProvider((spec, o) -> {
			ContractFunctionResult result = (ContractFunctionResult) o;
			Assertions.assertEquals(
					msg, Optional.ofNullable(result.getErrorMessage()).orElse(""),
					"Wrong contract function error!");
		});
		return this;
	}

	public ContractFnResultAsserts gasUsed(long gasUsed) {
		registerProvider((spec, o) -> {
			ContractFunctionResult result = (ContractFunctionResult) o;
			Assertions.assertEquals(
					gasUsed, result.getGasUsed(),
					"Wrong amount of Gas was used!");
		});
		return this;
	}

	public ContractFnResultAsserts stateChanges(StateChange ...stateChanges) {
		registerProvider((spec, o) -> {
			ContractFunctionResult result = (ContractFunctionResult) o;
			Assertions.assertEquals(
					UtilStateChange.stateChangesToGrpc(List.of(stateChanges), spec), result.getStateChangesList(),
					"Wrong state changes!");
		});
		return this;
	}

	public ContractFnResultAsserts contractCallResult(ContractCallResult contractCallResult) {
		registerProvider((spec, o) -> {
			ContractFunctionResult result = (ContractFunctionResult) o;
			Assertions.assertEquals(
					ByteString.copyFrom(contractCallResult.getBytes().toArray()),
					result.getContractCallResult(),
					"Wrong contract call result!");
		});
		return this;
	}

	public ContractFnResultAsserts gas(long gas) {
		registerProvider((spec, o) -> {
			ContractFunctionResult result = (ContractFunctionResult) o;
			Assertions.assertEquals(
					gas, result.getGas(),
					"Wrong amount of initial Gas!");
		});
		return this;
	}

	public ContractFnResultAsserts amount(long amount) {
		registerProvider((spec, o) -> {
			ContractFunctionResult result = (ContractFunctionResult) o;
			Assertions.assertEquals(
					amount, result.getAmount(),
					"Wrong amount of tinybars!");
		});
		return this;
	}

	public ContractFnResultAsserts functionParameters(Bytes functionParameters) {
		registerProvider((spec, o) -> {
			ContractFunctionResult result = (ContractFunctionResult) o;
			Assertions.assertEquals(
					ByteString.copyFrom(functionParameters.toArray()), result.getFunctionParameters(),
					"Wrong function parameters!");
		});
		return this;
	}

	/* Helpers to create the provider for #resultThruAbi. */
	public static Function<HapiApiSpec, Function<Object[], Optional<Throwable>>> isContractWith(
			ContractInfoAsserts theExpectedInfo) {
		return spec -> actualObjs -> {
			try {
				Assertions.assertEquals(1, actualObjs.length, "Extra contract function return values!");
				String implicitContract = "contract" + new Random().nextInt();
				ContractID contract = TxnUtils.asContractId((byte[]) actualObjs[0]);
				spec.registry().saveContractId(implicitContract, contract);
				HapiGetContractInfo op = getContractInfo(implicitContract).has(theExpectedInfo);
				Optional<Throwable> opError = op.execFor(spec);
				if (opError.isPresent()) {
					throw opError.get();
				}
			} catch (Throwable t) {
				return Optional.of(t);
			}
			return Optional.empty();
		};
	}

	public static Function<HapiApiSpec, Function<Object[], Optional<Throwable>>> isComputedResult(
			Function<HapiApiSpec, Object[]> resultProvider
	) {
		return spec -> actualObjs -> matchErrors(resultProvider.apply(spec), actualObjs);
	}

	public static Function<HapiApiSpec, Function<Object[], Optional<Throwable>>> isLiteralResult(Object[] objs) {
		return ignore -> actualObjs -> matchErrors(objs, actualObjs);
	}

	public static Function<HapiApiSpec, Function<Object[], Optional<Throwable>>> isLiteralArrayResult(Object[] objs) {
		return ignore -> actualObjs -> matchErrors(objs, (Object[]) actualObjs[0]);
	}

	private static Optional<Throwable> matchErrors(Object[] expecteds, Object[] actuals) {
		try {
			for (int i = 0; i < Math.max(expecteds.length, actuals.length); i++) {
				try {
					Object expected = expecteds[i];
					Object actual = actuals[i];
					Assertions.assertNotNull(expected);
					Assertions.assertNotNull(actual);
					Assertions.assertEquals(expected.getClass(), actual.getClass());
					if (expected instanceof byte[]) {
						Assertions.assertArrayEquals((byte[]) expected, (byte[]) actual);
					} else {
						Assertions.assertEquals(expected, actual);
					}
				} catch (Throwable T) {
					return Optional.of(T);
				}
			}
		} catch (Throwable T) {
			return Optional.of(T);
		}
		return Optional.empty();
	}
}
