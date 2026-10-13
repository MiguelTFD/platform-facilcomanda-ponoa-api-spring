package com.facilcomanda.ponoa.billing;

import com.facilcomanda.ponoa.billing.payload.CustomerLookupResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

  private final CustomerLookupService customerLookupService;

  public CustomerController(CustomerLookupService customerLookupService) {
    this.customerLookupService = customerLookupService;
  }

  @GetMapping("/lookup")
  public ResponseEntity<CustomerLookupResponse> lookupCustomer(
      @RequestParam(value = "documentType", required = false) String documentType,
      @RequestParam("documentNumber") String documentNumber) {

    CustomerLookupResponse response =
        customerLookupService.lookupCustomer(documentType, documentNumber);

    return ResponseEntity.ok(response);
  }
}
