package com.fabrics.pocket.bridge

import org.json.JSONObject

// 결과 코드는 브릿지 계약 문서의 레지스트리 표가 원본이다. 이름을 그대로 옮긴다.

enum class CancelCode { USER_CANCELED, PERMISSION_DENIED, PERMISSION_BLOCKED }

enum class ErrorCode { UNSUPPORTED, INVALID_PARAMS, BUSY, UNAVAILABLE, INTERNAL }

sealed interface BridgeResult {
    data class Ok(val data: JSONObject = JSONObject()) : BridgeResult
    data class Canceled(val code: CancelCode) : BridgeResult
    data class Error(val code: ErrorCode) : BridgeResult
}

/** 메서드 하나의 구현. 사용자 조작을 기다리는 메서드가 있으므로 결과는 콜백으로 한 번 돌려준다. */
fun interface BridgeHandler {
    fun handle(params: JSONObject, reply: (BridgeResult) -> Unit)
}
