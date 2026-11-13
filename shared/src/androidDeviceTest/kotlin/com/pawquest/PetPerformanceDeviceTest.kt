package com.pawquest

import android.content.Intent
import android.os.Bundle
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.platform.app.InstrumentationRegistry
import com.pawquest.presentation.scene.PrototypePetView
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Isolated real packaged SVG renderer; no authentication or economic writes. */
class PetPerformanceActivity : ComponentActivity() {
    internal lateinit var web: PrototypePetView
    var ready=CountDownLatch(1)
    fun pet(path: String) {
        if(::web.isInitialized) web.release()
        ready=CountDownLatch(1)
        web=PrototypePetView(this,assets.open(path).bufferedReader().use {it.readText()})
        web.onReady={ready.countDown()}
        setContentView(FrameLayout(this).also {it.addView(web,FrameLayout.LayoutParams(-1,-1))})
    }
    override fun onCreate(savedInstanceState: Bundle?) {super.onCreate(savedInstanceState);pet("pet/puppy_stage.html")}
    override fun onPause() {if(::web.isInitialized) web.pauseIllustration();super.onPause()}
    override fun onResume() {super.onResume();if(::web.isInitialized) web.resumeIllustration()}
    override fun onDestroy() {if(::web.isInitialized) web.release();super.onDestroy()}
}

class PetPerformanceDeviceTest {
    @Test fun duplicateDisplayUpdatesAndLifecycleKeepVisualStateCorrect() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val activity=instrumentation.startActivitySync(Intent(instrumentation.context,PetPerformanceActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as PetPerformanceActivity
        fun js(command: String): String {
            val done=CountDownLatch(1);var value=""
            instrumentation.runOnMainSync {activity.web.evaluateJavascript(command) {value=it;done.countDown()}}
            assertTrue(done.await(10,TimeUnit.SECONDS));return value
        }
        try {
            assertTrue(activity.ready.await(15,TimeUnit.SECONDS))
            js("window.__displayCalls=0;window.__originalRender=window.pawquestRender;window.pawquestRender=v=>{window.__displayCalls++;window.__lastDisplay=v;window.__originalRender(v)};true")
            val equipment=mutableListOf("acc-crown")
            instrumentation.runOnMainSync {repeat(100) {activity.web.update("idle",equipment,emptyList())}}
            assertEquals("1",js("window.__displayCalls"))
            equipment.clear()
            instrumentation.runOnMainSync {activity.web.update("idle",equipment,emptyList())}
            assertEquals("2",js("window.__displayCalls"));assertEquals("0",js("window.__lastDisplay.equipment.length"))
            instrumentation.runOnMainSync {activity.web.pauseIllustration();activity.web.pauseIllustration()}
            assertEquals("true",js("document.documentElement.classList.contains('pawquest-paused')"))
            instrumentation.runOnMainSync {activity.web.resumeIllustration();activity.web.resumeIllustration()}
            assertEquals("false",js("document.documentElement.classList.contains('pawquest-paused')"))
        } finally {instrumentation.runOnMainSync {activity.finish()}}
    }

    @Test fun rendererProfileAndLifecycle() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val activity=instrumentation.startActivitySync(Intent(instrumentation.context,PetPerformanceActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as PetPerformanceActivity
        fun js(command: String): String {
            val done=CountDownLatch(1);var result=""
            instrumentation.runOnMainSync {activity.web.evaluateJavascript(command) {result=it;done.countDown()}}
            assertTrue(done.await(10,TimeUnit.SECONDS));return result
        }
        fun runtime(name: String)=Debug.getRuntimeStat(name)?.toLongOrNull() ?: 0L
        fun mainCpu(): Long {var value=0L;instrumentation.runOnMainSync {value=Debug.threadCpuTimeNanos()};return value}
        val report=JSONObject()
        try {
            assertTrue(activity.ready.await(15,TimeUnit.SECONDS));SystemClock.sleep(2000)
            for(interact in listOf(false,true)) {
                val gc=runtime("art.gc.gc-count");val cpu=Process.getElapsedCpuTime();val main=mainCpu()
                val start=SystemClock.elapsedRealtime()
                js("window.__perf={d:[],last:null,active:true};(function f(t){const p=window.__perf;if(!p.active)return;if(p.last!==null)p.d.push(t-p.last);p.last=t;requestAnimationFrame(f)})(performance.now());true")
                var step=0
                while(SystemClock.elapsedRealtime()-start<30_000) {
                    if(interact) instrumentation.runOnMainSync {activity.web.update(if(step++%2==0) "laughing" else "idle",emptyList(),emptyList())}
                    SystemClock.sleep(if(interact) 500 else 1000)
                }
                val samples=JSONArray(js("window.__perf.active=false;JSON.stringify(window.__perf.d)").let {org.json.JSONTokener(it).nextValue().toString()})
                val sorted=(0 until samples.length()).map {samples.getDouble(it)}.sorted()
                assertTrue(sorted.size>30)
                fun p(q: Double)=sorted[((sorted.size-1)*q).toInt()]
                val memory=Debug.MemoryInfo().also {Debug.getMemoryInfo(it)}
                report.put(if(interact) "interaction30s" else "idle30s",JSONObject()
                    .put("raf_samples",sorted.size).put("raf_mean_ms",sorted.average()).put("raf_cadence_hz",1000/sorted.average())
                    .put("p50_ms",p(.5)).put("p90_ms",p(.9)).put("p95_ms",p(.95)).put("p99_ms",p(.99))
                    .put("raf_over_25ms_pct",100.0*sorted.count {it>25}/sorted.size)
                    .put("app_cpu_pct",100.0*(Process.getElapsedCpuTime()-cpu)/(SystemClock.elapsedRealtime()-start))
                    .put("main_cpu_ms",(mainCpu()-main)/1_000_000.0).put("app_pss_kb",memory.totalPss)
                    .put("gc_count",runtime("art.gc.gc-count")-gc))
            }
            instrumentation.runOnMainSync {
                activity.web.update("idle",emptyList(),emptyList())
                val started=SystemClock.elapsedRealtimeNanos()
                repeat(20_000) {activity.web.update("idle",emptyList(),emptyList())}
                report.put("identical_20000_updates_ms",(SystemClock.elapsedRealtimeNanos()-started)/1_000_000.0)
            }
            // All four real assets, visual state changes, pause/resume and release.
            for(path in listOf("prototype_stage","puppy_stage","rabbit_stage","capybara_stage")) {
                instrumentation.runOnMainSync {activity.pet("pet/$path.html")}
                assertTrue(activity.ready.await(15,TimeUnit.SECONDS))
                assertEquals("true",js("!!document.querySelector('svg') && typeof window.pawquestRender==='function'"))
                instrumentation.runOnMainSync {activity.web.update("eating",emptyList(),emptyList());activity.web.pauseIllustration()}
                instrumentation.runOnMainSync {activity.web.resumeIllustration();activity.web.update("idle",emptyList(),emptyList())}
            }
            val label=InstrumentationRegistry.getArguments().getString("perfLabel","run").replace(Regex("[^a-zA-Z0-9_-]"),"_")
            File(instrumentation.targetContext.getExternalFilesDir(null),"pet-perf-$label.json").writeText(report.toString(2))
        } finally {instrumentation.runOnMainSync {activity.finish()}}
    }
}
