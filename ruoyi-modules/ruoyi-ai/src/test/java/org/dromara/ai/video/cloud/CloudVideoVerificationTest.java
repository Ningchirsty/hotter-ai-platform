package org.dromara.ai.video.cloud;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.dromara.ai.video.exception.VideoTaskException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CloudVideoVerificationTest {
    @TempDir Path temp;
    CloudVideoRequest draft(String model, int seconds){return new CloudVideoRequest(model,"T2V","scene",seconds,"720p","16:9",List.of(),true,null,null,"key");}
    VideoCloudProperties properties(){var p=new VideoCloudProperties();p.setVerificationFile(temp.resolve("verification.json").toString());p.setValidationLimit(4);p.setValidationUserIds(List.of(7L));p.setValidationVariants(List.of("a|T2V|5|720p|16:9|true","b|T2V|5|720p|16:9|true","c|T2V|5|720p|16:9|true","d|T2V|5|720p|16:9|true","e|T2V|5|720p|16:9|true"));return p;}
    @Test void paidLimitSurvivesRestartsAndEachModelIsReservedOnlyOnce(){var p=properties();var v=new VideoCloudVerification(p);v.reserve(draft("a",5),7,11);assertEquals(3,v.remaining());var initial=v;assertThrows(VideoTaskException.class,()->initial.reserve(draft("a",5),7,12));v=new VideoCloudVerification(p);assertEquals(3,v.remaining());v.reserve(draft("b",5),7,12);v.reserve(draft("c",5),7,13);v.reserve(draft("d",5),7,14);assertEquals(0,v.remaining());var full=v;assertThrows(VideoTaskException.class,()->full.reserve(draft("e",5),7,15));}
    @Test void otherUsersCannotUseValidationAndChangedCombinationsStayBlocked(){var v=new VideoCloudVerification(properties());assertThrows(VideoTaskException.class,()->v.requireAllowed(draft("a",5),8));assertThrows(VideoTaskException.class,()->v.requireAllowed(draft("a",10),7));}
    @Test void onlyMeasuredTaskCanPromoteItsExactCombination(){var p=properties();var v=new VideoCloudVerification(p);v.reserve(draft("a",5),7,11);v.passed(draft("a",5),12);assertFalse(v.verified(draft("a",5)));v.passed(draft("a",5),11);assertTrue(v.verified(draft("a",5)));assertFalse(v.verified(draft("a",10)));var reloaded=new VideoCloudVerification(p);assertTrue(reloaded.verified(draft("a",5)));reloaded.requireAllowed(draft("a",5),8);}
    @Test void corruptedLedgerNeverResetsThePaidBudget()throws Exception{var p=properties();Files.writeString(Path.of(p.getVerificationFile()),"broken");assertThrows(IllegalStateException.class,()->new VideoCloudVerification(p));}
    @Test void failedPersistenceStopsBeforeAnAttemptIsReserved()throws Exception{var p=properties();Path file=temp.resolve("parent");Files.writeString(file,"not a directory");p.setVerificationFile(file.resolve("ledger.json").toString());var v=new VideoCloudVerification(p);assertThrows(VideoTaskException.class,()->v.reserve(draft("a",5),7,11));assertEquals(4,v.remaining());}
}
