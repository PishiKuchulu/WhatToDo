package com.example.pfptodo.domain.parser

import com.example.pfptodo.domain.model.TaskParseResult
import java.time.Clock

/**
 * Domain interface for parsing natural language task inputs.
 */
interface TaskParser {
    /**
     * Parses the given natural language [input] string into structured task data.
     *
     * @param input Raw text typed or spoken by the user.
     * @param clock Time provider used for relative date/time calculations (defaults to system default).
     * @return [TaskParseResult] containing the parsed task and confidence score.
     */
    fun parse(input: String, clock: Clock = Clock.systemDefaultZone()): TaskParseResult
}
