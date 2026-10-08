package de.samply.samplexchange.utils.fhir;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Bundle;

public abstract class FhirExportInterface {
    public FhirContext ctx;

    public abstract Boolean export(Bundle bundle);
}
