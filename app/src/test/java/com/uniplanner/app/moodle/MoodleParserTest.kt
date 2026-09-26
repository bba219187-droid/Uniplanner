package com.uniplanner.app.moodle

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MoodleParserTest {
    @Test
    fun tokenIsReadFromLoginResponse() {
        assertEquals("abc123", MoodleParser.token("""{"token":"abc123","privatetoken":null}"""))
    }

    @Test
    fun loginErrorKeepsMoodleCode() {
        try {
            MoodleParser.token("""{"error":"Invalid login, please try again","errorcode":"invalidlogin"}""")
            fail("expected an exception")
        } catch (e: MoodleException) {
            assertEquals("invalidlogin", e.code)
        }
    }

    @Test
    fun webServiceExceptionIsRaised() {
        try {
            MoodleParser.parseResponse("""{"exception":"moodle_exception","errorcode":"invalidtoken","message":"Invalid token"}""")
            fail("expected an exception")
        } catch (e: MoodleException) {
            assertEquals("invalidtoken", e.code)
        }
    }

    @Test
    fun htmlPageIsNotMoodle() {
        try {
            MoodleParser.parseResponse("<html>login</html>")
            fail("expected an exception")
        } catch (e: MoodleException) {
            assertEquals("notmoodle", e.code)
        }
    }

    @Test
    fun assignmentsAreFlattenedWithDueDatesInMillis() {
        val json = JSONObject(
            """
            {"courses":[{"id":7,"fullname":"Cálculo","assignments":[
              {"id":11,"cmid":90,"course":7,"name":"Ficha 1","duedate":1760000000,
               "configs":[{"plugin":"file","subtype":"assignsubmission","name":"enabled","value":"1"}]},
              {"id":12,"cmid":91,"course":7,"name":"Texto","duedate":0,
               "configs":[{"plugin":"file","subtype":"assignsubmission","name":"enabled","value":"0"}]}
            ]}],"warnings":[]}
            """,
        )
        val list = MoodleParser.assignments(json)
        assertEquals(2, list.size)
        assertEquals(1_760_000_000_000L, list[0].dueAt)
        assertTrue(list[0].acceptsFiles)
        assertEquals(null, list[1].dueAt)
        assertFalse(list[1].acceptsFiles)
    }

    @Test
    fun submissionStatusIsMapped() {
        val json = JSONObject("""{"lastattempt":{"submission":{"status":"submitted"}}}""")
        assertEquals(SubmissionState.SUBMITTED, MoodleParser.submissionState(json))
    }

    @Test
    fun uploadReturnsDraftItemId() {
        assertEquals(555L, MoodleParser.uploadedItemId(JSONArray("""[{"itemid":555,"filename":"a.pdf"}]""")))
    }

    @Test
    fun warningsBecomeErrors() {
        MoodleParser.checkWarnings(JSONArray("[]"))
        try {
            MoodleParser.checkWarnings(JSONArray("""[{"warningcode":"couldnotsavesubmission","message":"Could not save"}]"""))
            fail("expected an exception")
        } catch (e: MoodleException) {
            assertEquals("couldnotsavesubmission", e.code)
        }
    }

    @Test
    fun siteAddressIsNormalised() {
        assertEquals("https://moodle.uni.pt", MoodleClient.normalizeSite(" moodle.uni.pt/ "))
        assertEquals("http://localhost/moodle", MoodleClient.normalizeSite("http://localhost/moodle"))
    }
}
