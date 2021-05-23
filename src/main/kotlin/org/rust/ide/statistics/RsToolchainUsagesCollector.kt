/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Version
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.RsLocalToolchain
import org.rust.cargo.toolchain.RsRemoteToolchain
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.cargo.toolchain.RustChannel
import org.rust.cargo.toolchain.tools.isRustupAvailable

@Suppress("UnstableApiUsage")
class RsToolchainUsagesCollector : ProjectUsagesCollector() {
    override fun getGroup(): EventLogGroup = GROUP

    override fun getMetrics(project: Project): Set<MetricEvent> {
        val cargoProjects = project.cargoProjects.allProjects
        if (cargoProjects.isEmpty()) return emptySet()

        val metrics = mutableSetOf<MetricEvent>()
        val rustcVersion = cargoProjects.first().rustcInfo?.version
        if (rustcVersion != null) {
            val version = Version(rustcVersion.semver.major, rustcVersion.semver.minor, rustcVersion.semver.patch)
            metrics += COMPILER_EVENT.metric(
                version,
                rustcVersion.channel,
                rustcVersion.host
            )
        }
        val toolchain = project.toolchain
        metrics += RUSTUP_EVENT.metric(toolchain?.isRustupAvailable ?: false)
        metrics += TYPE_EVENT.metric(ToolchainType.from(toolchain))

        return metrics
    }

    companion object {
        private val GROUP = EventLogGroup("rust.toolchain", 1)

        private val VersionByObject = object : PrimitiveEventField<Version?>() {
            override val name: String = "version"
            override val validationRule: List<String>
                get() = listOf("{regexp#version}")

            override fun addData(fuData: FeatureUsageData, value: Version?) {
                fuData.addVersion(value)
            }
        }

        private val COMPILER_EVENT = GROUP.registerEvent("compiler",
            // BACKCOMPAT: 2020.3. Use `EventFields.VersionByObject` instead and drop our copy
            VersionByObject,
            EventFields.Enum<RustChannel>("channel"),
            EventFields.String("host_target", listOf(
                "i686-pc-windows-gnu",
                "i686-pc-windows-msvc",
                "i686-unknown-linux-gnu",
                "x86_64-apple-darwin",
                "x86_64-pc-windows-gnu",
                "x86_64-pc-windows-msvc",
                "x86_64-unknown-linux-gnu",
                "aarch64-unknown-linux-gnu",
                "aarch64-apple-darwin",
                "aarch64-pc-windows-msvc"
            ))
        )

        private val RUSTUP_EVENT = GROUP.registerEvent("rustup", EventFields.Boolean("used"))
        private val TYPE_EVENT = GROUP.registerEvent("type", EventFields.Enum<ToolchainType>("type"))
    }

    private enum class ToolchainType {
        LOCAL,
        WSL,
        NONE,
        OTHER;

        companion object {
            fun from(toolchain: RsToolchainBase?): ToolchainType {
                return when (toolchain) {
                    null -> NONE
                    is RsLocalToolchain -> LOCAL
                    // BACKCOMPAT: 2020.3. Use RsWslToolchain
                    is RsRemoteToolchain -> WSL
                    else -> OTHER
                }
            }
        }
    }
}
