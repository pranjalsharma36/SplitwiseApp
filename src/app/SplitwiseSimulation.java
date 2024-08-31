package app;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

// Main class to run the simulation
public class SplitwiseSimulation {
    public static void main(String[] args) {
        // Create instances of the required services
        ExpenseManager expenseManager = new ExpenseManager();
        BalanceSheetManager balanceSheetManager = new BalanceSheetManager();
        ValidationStrategyFactory validationStrategyFactory = new ValidationStrategyFactory();
        
        ExpenseService expenseService = new ExpenseService(expenseManager, balanceSheetManager, validationStrategyFactory);

        // Create users
        User user1 = new User("user1");
        User user2 = new User("user2");
        User user3 = new User("user3");

        // Create splits
        List<Split> splits = new ArrayList<>();
        splits.add(new EqualSplit(50, user1));
        splits.add(new EqualSplit(50, user2));

        // Create an expense
        expenseService.createExpense(user3, 100, splits, SplitType.EQUAL);

        // Print the balance sheet for verification
        balanceSheetManager.printBalanceSheet();
    }
}

// ExpenseService.java
class ExpenseService {

    ExpenseManager expenseManager;
    BalanceSheetManager balanceSheetManager;
    ValidationStrategyFactory validationStrategyFactory;

    public ExpenseService(ExpenseManager expenseManager, BalanceSheetManager balanceSheetManager,
                          ValidationStrategyFactory validationStrategyFactory) {
        this.expenseManager = expenseManager;
        this.balanceSheetManager = balanceSheetManager;
        this.validationStrategyFactory = validationStrategyFactory;
    }

    public void createExpense(User paidBy, int amount, List<Split> splits, SplitType type) {
        boolean validate = validate(amount, splits, type);
        if (!validate) {
            System.out.println("Not a valid Expense");
            return;
        }
        expenseManager.createExpense(paidBy, amount, splits);
        createExpensePostProcessing(paidBy, amount, splits, type);
    }

    public boolean validate(int amount, List<Split> splits, SplitType type) {
        ValidationStrategy validationStrategy = validationStrategyFactory.createInstance(type);
        return validationStrategy.validate(amount, splits);
    }

    public void createExpensePostProcessing(User paidBy, int amount, List<Split> splits, SplitType type) {
        for (Split split : splits) {
            if (split.user.id.equals(paidBy.id))
                continue;

            balanceSheetManager.updateBalanceSheet(paidBy, split.user, split.amount);
        }
    }
}

// ValidationStrategyFactory.java
class ValidationStrategyFactory {

    public ValidationStrategy createInstance(SplitType type) {
        switch (type) {
            case EQUAL:
                return new EqualValidationStrategy();
            case FIXED:
                return new FixedValidationStrategy();
            case PERCENTAGE:
                return new PercentageValidationStrategy();
            default:
                throw new IllegalArgumentException("Invalid SplitType");
        }
    }
}

// ValidationStrategy.java
interface ValidationStrategy {
    boolean validate(int amount, List<Split> splits);
}

// EqualValidationStrategy.java
class EqualValidationStrategy implements ValidationStrategy {

    public boolean validate(int amount, List<Split> splits) {
        int total = splits.size();
        int equalAmount = amount / total;
        for (Split split : splits) {
            if (split.amount != equalAmount) {
                return false;
            }
        }
        return true;
    }
}

// FixedValidationStrategy.java
class FixedValidationStrategy implements ValidationStrategy {

    public boolean validate(int amount, List<Split> splits) {
        int currAmount = 0;
        for (Split split : splits) {
            currAmount += split.amount;
        }
        return currAmount == amount;
    }
}

// PercentageValidationStrategy.java
class PercentageValidationStrategy implements ValidationStrategy {

    public boolean validate(int amount, List<Split> splits) {
        int currAmount = 0;
        for (Split split : splits) {
            int percentage = split.percentage;
            int percentageAmount = (amount * percentage) / 100;
            if (percentageAmount != split.amount)
                return false;
            currAmount += split.amount;
        }
        return currAmount == amount;
    }
}

// SplitType.java
enum SplitType {
    EQUAL,
    FIXED,
    PERCENTAGE
}

// Split.java
class Split {
    SplitType type;
    int amount;
    User user;

    public Split(SplitType type, int amount, User user) {
        this.type = type;
        this.amount = amount;
        this.user = user;
    }
}

// EqualSplit.java
class EqualSplit extends Split {
    public EqualSplit(int amount, User user) {
        super(SplitType.EQUAL, amount, user);
    }
}

// FixedSplit.java
class FixedSplit extends Split {
    public FixedSplit(int amount, User user) {
        super(SplitType.FIXED, amount, user);
    }
}

// PercentageSplit.java
class PercentageSplit extends Split {
    int percentage;

    public PercentageSplit(int amount, User user, int percentage) {
        super(SplitType.PERCENTAGE, amount, user);
        this.percentage = percentage;
    }
}

// User.java
class User {
    String id;

    public User(String id) {
        this.id = id;
    }
}

// ExpenseManager.java
class ExpenseManager {
    HashMap<String, Expense> expenseCatalog;

    public ExpenseManager() {
        this.expenseCatalog = new HashMap<>();
    }

    public void createExpense(User paidBy, int amount, List<Split> splits) {
        Expense expense = new Expense(paidBy, amount, splits);
        expenseCatalog.put(expense.id, expense);
    }
}

// BalanceSheetManager.java
class BalanceSheetManager {
    HashMap<String, HashMap<String, Balance>> balanceSheet;

    public BalanceSheetManager() {
        this.balanceSheet = new HashMap<>();
    }

    public void updateBalanceSheet(User payTo, User payFrom, int amount) {
        balanceSheet.putIfAbsent(payTo.id, new HashMap<>());
        balanceSheet.putIfAbsent(payFrom.id, new HashMap<>());

        HashMap<String, Balance> payToBalances = balanceSheet.get(payTo.id);
        HashMap<String, Balance> payFromBalances = balanceSheet.get(payFrom.id);

        Balance payToBalance = payToBalances.getOrDefault(payFrom.id, new Balance(0, 0));
        Balance payFromBalance = payFromBalances.getOrDefault(payTo.id, new Balance(0, 0));

        int netAmount = payToBalance.getBack - payFromBalance.toPay + amount;

        if (netAmount > 0) {
            payToBalance.getBack = netAmount;
            payFromBalance.toPay = 0;
        } else if (netAmount < 0) {
            payToBalance.getBack = 0;
            payFromBalance.toPay = -netAmount;
        } else {
            payToBalance.getBack = 0;
            payFromBalance.toPay = 0;
        }

        payToBalances.put(payFrom.id, payToBalance);
        payFromBalances.put(payTo.id, payFromBalance);
    }

    public void printBalanceSheet() {
        for (String userId : balanceSheet.keySet()) {
            HashMap<String, Balance> balances = balanceSheet.get(userId);
            System.out.println("Balances for user: " + userId);
            for (String otherUserId : balances.keySet()) {
                Balance balance = balances.get(otherUserId);
                System.out.println("  With user: " + otherUserId + " - To Pay: " + balance.toPay + ", Get Back: " + balance.getBack);
            }
        }
    }
}

// Balance.java
class Balance {
    int toPay;
    int getBack;

    public Balance(int toPay, int getBack) {
        this.toPay = toPay;
        this.getBack = getBack;
    }
}

// Expense.java
class Expense {
    String id;
    User paidBy;
    int amount;
    List<Split> splits;

    public Expense(User paidBy, int amount, List<Split> splits) {
        this.id = UUID.randomUUID().toString();
        this.paidBy = paidBy;
        this.amount = amount;
        this.splits = splits;
    }
}

