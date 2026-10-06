package net.lunacy.visuals.render;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AdaptiveBudgetTest {
 @Test void shedsLoadQuicklyAndRecoversGradually(){AdaptiveBudget b=new AdaptiveBudget();for(int i=0;i<40;i++)b.sample(25,90);double low=b.factor();assertTrue(low<.8);for(int i=0;i<10;i++)b.sample(200,90);assertTrue(b.factor()>low);assertTrue(b.factor()<1);}
 @Test void staysBoundedAndIgnoresInvalidSamples(){AdaptiveBudget b=new AdaptiveBudget();assertEquals(1,b.sample(Double.NaN,90));for(int i=0;i<200;i++)b.sample(1,90);assertEquals(.2,b.factor(),.0001);}
}
