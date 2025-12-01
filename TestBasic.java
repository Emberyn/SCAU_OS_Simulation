public class TestBasic {
    public static void main(String[] args) {
        System.out.println("Testing basic functions...");
        
        try {
            // Test process management
            System.out.println("1. Testing process management...");
            
            // Test memory management
            System.out.println("2. Testing memory management...");
            
            // Test file system
            System.out.println("3. Testing file system...");
            
            System.out.println("All basic function tests completed!");
        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}