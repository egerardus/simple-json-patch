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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A simple (one class) implementation of 
 * <a href="https://tools.ietf.org/html/rfc6902">RFC 6902 JSON Patch</a> using Jackson. 
 * <p>
 * This class just applies a patch to a JSON document, nothing fancy like diffs 
 * or patch generation.
 * </p>
 */
public class JsonPatch
{    
    /**
     * Applies all JSON patch operations to a JSON document. 
     * @return the patched JSON document
     */
    public JsonNode apply(ArrayNode patch, JsonNode source)
    {
        if (!source.isContainerNode())
        {
            throw new IllegalArgumentException("Invalid JSON document, "
                    + "an object or array is required");
        }

        JsonNode result = source.deepCopy();
        if (patch.size() == 0)
        {
            return result;
        }
        
        for (JsonNode operation : patch)
        {
            if (!operation.isObject())
            {
                throw new IllegalArgumentException("Invalid operation: " + operation);
            }
            result = perform((ObjectNode) operation, result);
        }
        
        return result;
    }
    
    /**
     * Perform one JSON patch operation
     * @return the patched JSON document
     */
    protected JsonNode perform(ObjectNode operation, JsonNode doc)
    {        
        JsonNode opNode = operation.get("op");
        if (opNode == null || !opNode.isTextual())
        {
            throw new IllegalArgumentException("Invalid \"op\" property: " + opNode);
        }
        String op = opNode.asText();
        JsonNode pathNode = operation.get("path");
        if (pathNode == null || !pathNode.isTextual())
        {
            throw new IllegalArgumentException("Invalid \"path\" property: " + pathNode);
        }
        String path = pathNode.asText();
        if (path.length() != 0 && path.charAt(0) != '/')
        {
            throw new IllegalArgumentException("Invalid \"path\" property: " + path);
        }
        
        switch (op)
        {
        
        case "add":
        {
            JsonNode value = operation.get("value");
            if (value == null)
            {
                throw new IllegalArgumentException("Missing \"value\" property");
            }
            return add(doc, path, value);
        }
                        
        case "remove":
        {
            return remove(doc, path);
        }
            
        case "replace":
        {
            JsonNode value = operation.get("value");
            if (value == null)
            {
                throw new IllegalArgumentException("Missing \"value\" property");
            }
            return replace(doc, path, value);
        }
            
        case "move":
        {
            JsonNode fromNode = operation.get("from");
            if (fromNode == null || !fromNode.isTextual())
            {
                throw new IllegalArgumentException("Invalid \"from\" property: " + fromNode);
            }
            String from = fromNode.asText();
            if (from.length() != 0 && from.charAt(0) != '/')
            {
                throw new IllegalArgumentException("Invalid \"from\" property: " + fromNode);
            }
            return move(doc, path, from);
        }
        
        case "copy":
        {
            JsonNode fromNode = operation.get("from");
            if (fromNode == null || !fromNode.isTextual())
            {
                throw new IllegalArgumentException("Invalid \"from\" property: " + fromNode);
            }
            String from = fromNode.asText();
            if (from.length() != 0 && from.charAt(0) != '/')
            {
                throw new IllegalArgumentException("Invalid \"from\" property: " + fromNode);
            }
            return copy(doc, path, from);
        }
        
        case "test":
        {
            JsonNode value = operation.get("value");
            if (value == null)
            {
                throw new IllegalArgumentException("Missing \"value\" property");
            }
            return test(doc, path, value);
        }
        
        default:
            throw new IllegalArgumentException("Invalid \"op\" property: " + op);
        }
    }

    /**
     * Perform a JSON patch "add" operation on a JSON document
     * @return the patched JSON document
     */
    protected JsonNode add(JsonNode doc, String path, JsonNode value)
    {
        if (path.isEmpty())
        {
            return value;
        }
        
        // get the path parent
        JsonNode parent = null;
        int lastPathIndex = path.lastIndexOf('/');
        if (lastPathIndex < 1) 
        {
            parent = doc;
        }
        else
        {
            String parentPath = path.substring(0, lastPathIndex);
            parent = doc.at(parentPath);
        }
        
        // adding to an object
        if (parent.isObject())
        {
            ObjectNode parentObject = (ObjectNode) parent;
            String key = path.substring(lastPathIndex + 1);
            parentObject.set(key, value);
        }
        
        // adding to an array
        else if (parent.isArray())
        {
            String key = path.substring(lastPathIndex + 1);
            ArrayNode parentArray = (ArrayNode) parent;
            if (key.equals("-"))
            {
                parentArray.add(value);
            }
            else
            {
                try
                {
                    int idx = Integer.parseInt(key);
                    if (idx > parentArray.size() || idx < 0)
                    {
                        throw new IllegalArgumentException("Array index is out of bounds: " + idx);
                    }
                    parentArray.insert(idx, value);
                }
                catch (NumberFormatException e)
                {
                    throw new IllegalArgumentException("Invalid array index: " + key);
                }
            }
        }
        
        else
        {
            throw new IllegalArgumentException("Invalid \"path\" property: " + path);
        }
        
        return doc;
    }

    /**
     * Perform a JSON patch "remove" operation on a JSON document
     * @return the patched JSON document
     */
    protected JsonNode remove(JsonNode doc, String path)
    {
        if (path.equals(""))
        {
            if (doc.isObject())
            {
                ObjectNode docObject = (ObjectNode) doc;
                docObject.removeAll();
                return doc;
            }
            else if (doc.isArray())
            {
                ArrayNode docArray = (ArrayNode) doc;
                docArray.removeAll();
                return doc;
            }
        }
        
        // get the path parent
        JsonNode parent = null;
        int lastPathIndex = path.lastIndexOf('/');
        if (lastPathIndex == 0) 
        {
            parent = doc;
        }
        else
        {
            String parentPath = path.substring(0, lastPathIndex);
            parent = doc.at(parentPath);
            if (parent.isMissingNode())
            {
                throw new IllegalArgumentException("Path does not exist: " + path);
            }
        }
        
        // removing from an object
        String key = path.substring(lastPathIndex + 1);
        if (parent.isObject())
        {
            ObjectNode parentObject = (ObjectNode) parent;
            if (!parent.has(key))
            {
                throw new IllegalArgumentException("Property does not exist: " + key);
            }
            parentObject.remove(key);
        }
        
        // removing from an array
        else if (parent.isArray())
        {
            try
            {
                ArrayNode parentArray = (ArrayNode) parent;
                int idx = Integer.parseInt(key);
                if (!parent.has(idx))
                {
                    throw new IllegalArgumentException("Index does not exist: " + key);
                }
                parentArray.remove(idx);
            }
            catch (NumberFormatException e)
            {
                throw new IllegalArgumentException("Invalid array index: " + key);
            }
        }
        
        else
        {
            throw new IllegalArgumentException("Invalid \"path\" property: " + path);
        }
        
        return doc;
    }

    /**
     * Perform a JSON patch "replace" operation on a JSON document
     * @return the patched JSON document
     */
    protected JsonNode replace(JsonNode doc, String path, JsonNode value)
    {
        doc = remove(doc, path);
        return add(doc, path, value);
    }

    /**
     * Perform a JSON patch "move" operation on a JSON document
     * @return the patched JSON document
     */
    protected JsonNode move(JsonNode doc, String path, String from)
    {        
        // get the value
        JsonNode value = doc.at(from);
        if (value.isMissingNode())
        {
            throw new IllegalArgumentException("Invalid \"from\" property: " + from);
        }
        
        // do remove and then add
        doc = remove(doc, from);
        return add(doc, path, value);
    }

    /**
     * Perform a JSON patch "copy" operation on a JSON document
     * @return the patched JSON document
     */
    protected JsonNode copy(JsonNode doc, String path, String from)
    {        
        // get the value
        JsonNode value = doc.at(from);
        if (value.isMissingNode())
        {
            throw new IllegalArgumentException("Invalid \"from\" property: " + from);
        }
        
        // do add
        return add(doc, path, value);
    }

    /**
     * Perform a JSON patch "test" operation on a JSON document
     * @return the patched JSON document
     */
    protected JsonNode test(JsonNode doc, String path, JsonNode value)
    {
        JsonNode node = doc.at(path);
        if (node.isMissingNode())
        {
            throw new IllegalArgumentException("Invalid \"path\" property: " + path);
        }
        
        if (!node.equals(value))
        {
            throw new IllegalArgumentException("The value does not equal path node");
        }
        
        return doc;
    }
}
