import java.math.BigDecimal;
import java.sql.*;
import java.util.InputMismatchException;
import java.util.Scanner;
import java.text.NumberFormat;



public class ATM {

    static String db = Login.DB;
    static String user = Login.USER;
    static String password = Login.PASSWORD;
    static String url = Login.URL;

    public static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);  
   }

    public static String padLeft(String s, int n) {
        return String.format("%" + n + "s", s);  
    }

    public static String formatMoney(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance().format(amount);
    }

    public static boolean query_credentials(int acc_num, String pin) {
        String query = "SELECT A.acc_num, C.cust_name, C.PIN FROM Customer C JOIN Account A USING (cust_name) WHERE A.acc_num = ? AND C.PIN = ?;";

        try (
            Connection conn = DriverManager.getConnection(url + db, user, password);
            PreparedStatement pstmt = conn.prepareStatement(query);
        ) {    
            pstmt.setInt(1, acc_num); 
            pstmt.setString(2, pin);

            ResultSet results = pstmt.executeQuery();

            if (results.next()) {
                return true;
            } else {
                return false;
            }
        } catch (SQLException e) {
            System.out.println("SQL Error " + e.getMessage());
        } catch (Exception e) {
            System.out.println("General Error " + e);
        }
        return false;
    }

    public static void balanceInquiry(int acc_num) {
        String query = "SELECT balance FROM Account WHERE acc_num = ?;";

        try (
            Connection conn = DriverManager.getConnection(url + db, user, password);
            PreparedStatement pstmt = conn.prepareStatement(query);
        ) {    
            pstmt.setInt(1, acc_num); 

            ResultSet results = pstmt.executeQuery();

            if (results.next()) {
                System.out.println("\nBalance for account " + acc_num + ": " + formatMoney(results.getBigDecimal(1)) + "\n");
            } else {
            }
        } catch (SQLException e) {
            System.out.println("SQL Error " + e.getMessage());
        } catch (Exception e) {
            System.out.println("General Error " + e);
        }
    }

    public static void miniStatement(int acc_num) {
        String balance_query = "SELECT balance FROM Account WHERE acc_num = ?;";
        String transactions_query = "SELECT transaction_timestamp, amount, description FROM SuccessfulTransactions WHERE acc_num = ? ORDER BY transaction_timestamp DESC";
        try (
            Connection conn = DriverManager.getConnection(url + db, user, password);
            PreparedStatement pstmt1 = conn.prepareStatement(balance_query);
            PreparedStatement pstmt2 = conn.prepareStatement(transactions_query);
        ) {    
            pstmt1.setInt(1, acc_num); 
            ResultSet results1 = pstmt1.executeQuery();

            BigDecimal running_balance = new BigDecimal(0);
            
            if (results1.next()) {
                running_balance = results1.getBigDecimal("balance");
            }
            else {
                return;
            }


            pstmt2.setInt(1, acc_num);
            ResultSet results2 = pstmt2.executeQuery();

            String tableLineBreak = "+----------+----------------------------------------------------------------------------------------------------+-----------------+-----------------+";

            System.out.println(tableLineBreak);
            System.out.println("|" + padLeft("Date", 10) + "|" + padLeft("Description", 100) + "|" + padLeft("Trans. Amount", 17) + "|"  + padLeft("Balance After", 17) + "|");
            System.out.println(tableLineBreak);


            while (results2.next()) {
                Date cur_timestamp = results2.getDate("transaction_timestamp");
                String cur_description = results2.getString("description");
                BigDecimal cur_amount = results2.getBigDecimal("amount");

                System.out.println(
                    "|" + cur_timestamp.toString() + 
                    "|" + padLeft(cur_description, 100) + 
                    "|" + padLeft(formatMoney(cur_amount), 17) + 
                    "|" + padLeft(formatMoney(running_balance), 17)  + "|"
                );

                System.out.println(tableLineBreak);

                running_balance = running_balance.subtract(cur_amount);
            }
            

            
        } catch (SQLException e) {
            System.out.println("SQL Error " + e.getMessage());
        } catch (Exception e) {
            System.out.println("General Error " + e);
        }
    }

    public static boolean cashWithdrawal(int acc_num, BigDecimal amountToWithdraw) {
        String update = "INSERT INTO AccountTransaction (acc_num, description, amount) VALUES (?,?,?);";
        String query = "SELECT transaction_id FROM OverdraftAttempt WHERE transaction_id = ?;";
        try (
            Connection conn = DriverManager.getConnection(url + db, user, password);
            PreparedStatement pstmt1 = conn.prepareStatement(update, Statement.RETURN_GENERATED_KEYS);
            PreparedStatement pstmt2 = conn.prepareStatement(query);
        ) {    
            pstmt1.setInt(1, acc_num); 
            pstmt1.setString(2, "ATM WITHDRAWAL");
            pstmt1.setBigDecimal(3, amountToWithdraw.negate());
            pstmt1.executeUpdate();

            ResultSet keysGenerated = pstmt1.getGeneratedKeys();
            keysGenerated.next();
            int keyForTransaction = keysGenerated.getInt(1);

            pstmt2.setInt(1, keyForTransaction);
            ResultSet overdraftAttempt = pstmt2.executeQuery();

            if (overdraftAttempt.next()) {
                return false;
            } else {
                return true;
            }


        } catch (SQLException e) {
            System.out.println("SQL Error " + e.getMessage());
        } catch (Exception e) {
            System.out.println("General Error " + e);
        }        
        return false;
    }

    public static void deposit(int acc_num, BigDecimal amountToDeposit) {
        String update = "INSERT INTO AccountTransaction (acc_num, description, amount) VALUES (?,?,?);";
        try (
            Connection conn = DriverManager.getConnection(url + db, user, password);
            PreparedStatement pstmt1 = conn.prepareStatement(update);
        ) {    
            pstmt1.setInt(1, acc_num); 
            pstmt1.setString(2, "ATM DEPOSIT");
            pstmt1.setBigDecimal(3, amountToDeposit);
            pstmt1.executeUpdate();
        } catch (SQLException e) {
            System.out.println("SQL Error " + e.getMessage());
        } catch (Exception e) {
            System.out.println("General Error " + e);
        }        
    }
    
    public static boolean pinChange(int acc_num, String newPin) {
        String update = "UPDATE Customer SET pin = ? WHERE cust_name = (SELECT * FROM (SELECT C.cust_name FROM Customer C JOIN Account A USING (cust_name) WHERE A.acc_num = ?) AS A)";
        try (
            Connection conn = DriverManager.getConnection(url + db, user, password);
            PreparedStatement pstmt = conn.prepareStatement(update);
        ) {    
            pstmt.setString(1, newPin);
            pstmt.setInt(2, acc_num);

            
            int numRowsAffected = pstmt.executeUpdate();
            if (numRowsAffected == 1) {
                return true;
            } else {
                return false;
            }

        } catch (SQLException e) {
            System.out.println("SQL Error " + e.getMessage());
        } catch (Exception e) {
            System.out.println("General Error " + e);
        }
        return false;
    }
    public static void main(String[] args) throws Exception {

    
        System.out.println("Please enter your account number on one line and your PIN on another:");

        Scanner scnr = new Scanner(System.in);

        int accNum = 0;
        String pin = "";

        boolean hasValidCreds = false;

        while (!hasValidCreds) {
            try {
                accNum = scnr.nextInt();
                pin = scnr.next();
                hasValidCreds = query_credentials(accNum, pin);
                if (!hasValidCreds) {
                    throw new Exception();
                }
            } catch (InputMismatchException e) {
                System.out.println("The account number must be an integer value, please try again:");
                scnr.nextLine();
            } catch (Exception e) {
                System.out.println("Invalid credentials, please try again:");
                scnr.nextLine();
            }

        }

        int mainMenuChoice = 0;

        while (mainMenuChoice != 6) {
            System.out.println();
            System.out.println("Authentication successful, select one of the following:");
            System.out.println("1   Balance Inquiry");
            System.out.println("2   Mini Statement");
            System.out.println("3   Cash Withdrawal");
            System.out.println("4   Deposit");
            System.out.println("5   PIN Change");
            System.out.println("6   Quit");
            System.out.println();

            boolean validMainMenuChoice = false;

            while (!validMainMenuChoice) {
                try {
                    mainMenuChoice = scnr.nextInt();
                    if (mainMenuChoice < 1 || mainMenuChoice > 6) {
                        throw new Exception();
                    } else {
                        validMainMenuChoice = true;
                    }   
                } catch (Exception e) {
                    System.out.println("\nInput must be a number between 1 and 6, please try again:\n");
                    scnr.nextLine();
                }
            }

            switch (mainMenuChoice) {
                case 1: 
                    balanceInquiry(accNum);
                    break;
                case 2:
                    miniStatement(accNum);
                    break;
                case 3:
                    System.out.println(
                        "\nPlease select one of the following:\n" +
                        "1   $20\n" +
                        "2   $40\n" +
                        "3   $60\n" +
                        "4   100\n" +
                        "5   $150\n" + 
                        "6   OTHER"
                    );

                    int withdrawalChoice = 0;
                    boolean validWithdrawalChoiceMade = false;

                    while (!validWithdrawalChoiceMade) {
                        try {
                            withdrawalChoice = scnr.nextInt();
                            if (withdrawalChoice < 1 || withdrawalChoice > 6) {
                                throw new Exception();
                            } else {
                                validWithdrawalChoiceMade = true;
                            }   
                        } catch (Exception e) {
                            System.out.println("\nInput must be a number between 1 and 6, please try again:\n");
                            scnr.nextLine();
                        }
                    }
                    
                    BigDecimal amountToWithdraw = new BigDecimal(0);

                    switch (withdrawalChoice) {
                        case 1:
                            amountToWithdraw = new BigDecimal(20);
                            break;
                        case 2:
                            amountToWithdraw = new BigDecimal(40);
                            break;
                        case 3:
                            amountToWithdraw  = new BigDecimal(60);
                            break;
                        case 4:
                            amountToWithdraw  = new BigDecimal(100);
                            break; 
                        case 5:
                            amountToWithdraw = new BigDecimal(150);
                            break;
                        case 6:
                            System.out.println("\nEnter amount to withdraw: ");


                            boolean validWithdrawalAmount = false;

                            while (!validWithdrawalAmount) {
                                try {
                                    amountToWithdraw = scnr.nextBigDecimal();
                                    if (amountToWithdraw.compareTo(BigDecimal.ZERO) <= 0) {
                                        throw new Exception();
                                    } else {
                                        validWithdrawalAmount = true;
                                    }
                                } catch (Exception e) {
                                    System.out.println("Amount must be a positve number, please try again:");
                                    scnr.nextLine();
                                }
                            }


                            break;
                    }

                    if (cashWithdrawal(accNum, amountToWithdraw)) {
                        System.out.println("Here is your $" + amountToWithdraw);
                    } else {
                        System.out.println("The withdrawal cannot be completed.");
                    }
                    break;
                case 4:
                    System.out.println("Enter deposit amount:");
                    
                    boolean validDepositAmount = false;
                    
                    BigDecimal amountToDeposit = new BigDecimal(0);

                    while (!validDepositAmount) {
                        try {
                            amountToDeposit = scnr.nextBigDecimal();
                            if (amountToDeposit.compareTo(BigDecimal.ZERO) <= 0) {
                                throw new Exception();
                            } else {
                                validDepositAmount = true;
                            }
                        } catch (Exception e) {
                            System.out.println("Amount must be a positve number, please try again:");
                            scnr.nextLine();
                        }
                    }

                    deposit(accNum, amountToDeposit);
                    System.out.println("Deposit Successful");
                    break;
                case 5: 
                    System.out.println("Enter new 4-digit PIN:");
                    
                    boolean validPin = false;
                    
                    String newPin1 = "";

                    while (!validPin) {
                        try {
                            newPin1 = scnr.next();
                            Integer.parseInt(newPin1);
                            if (newPin1.length() != 4) {
                                throw new Exception();
                            } else {
                                validPin = true;
                            }
                        } catch (Exception e) {
                            System.out.println("New PIN must be a 4-digit number, please try again:");
                            scnr.nextLine();
                        }
                    }


                    System.out.println("Re-enter new PIN to confirm:");
                    String newPin2 = scnr.next();

                    if (newPin1.equals(newPin2)) {
                        if (pinChange(accNum, newPin1)) {
                            System.out.println("PIN change successful");
                        } else {
                            System.out.println("PIN change unsuccessful");
                        }
                        
                    } else {
                        System.out.println("PINs do not match, cancelling PIN change");
                    }
                    break;
                case 6:
                    break;
            }
        }
        scnr.close();
    }
}
