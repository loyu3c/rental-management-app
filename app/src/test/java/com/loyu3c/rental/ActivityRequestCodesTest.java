package com.loyu3c.rental;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class ActivityRequestCodesTest {
    @Test public void externalActivityResultsHaveDistinctRequestCodes() throws Exception {
        Path source=Paths.get("src/main/java/com/loyu3c/rental/MainActivity.java");
        if(!Files.exists(source))source=Paths.get("app/src/main/java/com/loyu3c/rental/MainActivity.java");
        String code=Files.readString(source);Matcher declarations=Pattern.compile("private static final int ([^;]+);").matcher(code);
        Map<Integer,String> values=new HashMap<>();Set<String> names=new HashSet<>();
        while(declarations.find()){Matcher constants=Pattern.compile("(\\w+)\\s*=\\s*(\\d+)").matcher(declarations.group(1));while(constants.find()){
            String name=constants.group(1);int value=Integer.parseInt(constants.group(2));String old=values.put(value,name);assertNull("Activity request code collision: "+old+" and "+name,old);names.add(name);
        }}
        assertTrue(names.containsAll(Arrays.asList("EXPORT","IMPORT","CAMERA_PHOTO","PICK_PHOTO","VOICE_INPUT","EXPORT_REPORT","EXPORT_WORKBOOK","PICK_CONTRACT")));
    }
}
