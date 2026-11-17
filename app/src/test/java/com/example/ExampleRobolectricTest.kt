package com.example

import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Educators Results", appName)
  }

  @Test
  fun `manifest MainActivity class resolves`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val activityInfo = context.packageManager.getActivityInfo(
      ComponentName(context.packageName, "com.example.MainActivity"),
      0
    )
    assertNotNull(activityInfo)
    assertEquals("com.example.MainActivity", MainActivity::class.java.name)
  }
}
