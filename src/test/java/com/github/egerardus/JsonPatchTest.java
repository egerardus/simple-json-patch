/*
 * Copyright 2021 Simple JSON Patch contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.egerardus;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JsonPatchTest
{
    private static final ObjectMapper mapper = new ObjectMapper();
    private final Path testPatchPath = 
            Paths.get("src","test","resources","testPatches.json").toAbsolutePath();
    
    /**
     * Apply all test patches from <a href="https://github.com/json-patch/json-patch-tests/blob/master/tests.json">
     * https://github.com/json-patch/json-patch-tests/blob/master/tests.json</a>
     */
    @Test
    public void applyAllTestPatches()
    {
        JsonPatch jsonPatch = new JsonPatch();
        ArrayNode tests = null;
        try
        {
            byte[] testsBytes = Files.readAllBytes(testPatchPath);
            tests = (ArrayNode) mapper.readTree(testsBytes);
        }
        catch (IOException e)
        {
            fail();
        }
        
        for (int i = 0; i < tests.size(); i++)
        {
            ObjectNode test = (ObjectNode) tests.get(i);
            JsonNode doc = test.get("doc");
            ArrayNode patch = (ArrayNode) test.get("patch");
            JsonNode expected = test.get("expected");
            String error = test.findPath("error").textValue();
            String comment = test.findPath("comment").textValue();
            if (test.findPath("disabled").booleanValue())
            {
                continue;
            }

            try
            {
                doc = jsonPatch.apply(patch, doc);
                if (expected != null && !expected.equals(doc))
                {
                    fail("The patch (" + patch + ") failed, result (" + doc + 
                            ") does not meet expectations: " + expected);
                }
                else if (error != null)
                {
                    fail("The patch (" + patch + ") succeeded, but the result (" + doc + 
                            ") should have been an error: " + error);
                }
            }
            catch (IllegalArgumentException e)
            {
                if (error == null)
                {
                    if (comment != null)
                    {
                        fail("The patch (" + patch + ") failed with comment: " + comment);
                    }
                    else
                    {
                        fail("The patch (" + patch + ") failed");
                    }
                }
            }
        }
    }
}
