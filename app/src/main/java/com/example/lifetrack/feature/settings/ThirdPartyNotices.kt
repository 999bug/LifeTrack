package com.example.lifetrack.feature.settings

/**
 * 第三方许可文本。
 *
 * SQLCipher Community Edition 是 BSD-style 许可，要求在用户可访问的位置提供
 * 版权声明与许可全文；设置页「关于 → 开源许可」展示本文件的内容。
 * 原文来自 https://www.zetetic.net/sqlcipher/license/SQLCIPHER_COMMUNITY_EDITION_LICENSE.txt
 */
internal const val SQLCIPHER_COPYRIGHT = "Copyright (c) 2008-2026, ZETETIC, LLC\nAll rights reserved."

internal val SQLCIPHER_LICENSE = """
    Redistribution and use in source and binary forms, with or without
    modification, are permitted provided that the following conditions are met:
        * Redistributions of source code must retain the above copyright
          notice, this list of conditions and the following disclaimer.
        * Redistributions in binary form must reproduce the above copyright
          notice, this list of conditions and the following disclaimer in the
          documentation and/or other materials provided with the distribution.
        * Neither the name of the ZETETIC LLC nor the
          names of its contributors may be used to endorse or promote products
          derived from this software without specific prior written permission.

    THIS SOFTWARE IS PROVIDED BY ZETETIC LLC ''AS IS'' AND ANY
    EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
    WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
    DISCLAIMED. IN NO EVENT SHALL ZETETIC LLC BE LIABLE FOR ANY
    DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
    (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
    LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
    ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
    (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
    SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
""".trimIndent()

/** SQLCipher 依赖的开源组件；许可页要求一并列出。 */
internal val SQLCIPHER_COMPONENTS = """
    SQLCipher 内含或依赖以下组件：
    · SQLite / SQLite Android Bindings：公有领域（SQLite Project）
    · OpenSSL：Apache License 2.0 或 OpenSSL / SSLeay 双许可（OpenSSL Project）
    · LibTomCrypt：公有领域（LibTomCrypt project）
    · Android 平台库：Apache License 2.0（Android Open Source Project）
""".trimIndent()
