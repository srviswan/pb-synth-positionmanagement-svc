// FILE: src/main/java/com/bank/esps/model/DomainModel.java

import java.math.BigDecimal;
import java.util.List;

public class CompressedLots {
    // Parallel Arrays - Saves 40-60% JSON space vs Array of Objects
    private List<String> ids;      // ["L1", "L2"]
    private List<String> dates;    // ["2023-01-01", "2023-01-02"]
    private List<Double> prices;   // [100.50, 101.00]
    private List<Integer> qtys;    // [500, 300]
    
    // Helper to inflate back to Objects for Logic Processing
    public List<TaxLot> inflate() {
        List<TaxLot> list = new ArrayList<>();
        for (int i=0; i<ids.size(); i++) {
            list.add(new TaxLot(ids.get(i), dates.get(i), prices.get(i), qtys.get(i)));
        }
        return list;
    }
}

public class TaxLot {
    private String id;
    private String tradeDate;
    private BigDecimal currentRefPrice; // Updates on Reset
    private BigDecimal remainingQty;
    // ... constructors
}