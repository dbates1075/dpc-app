package gov.cms.dpc.api.resources;

import gov.cms.dpc.api.auth.OrganizationPrincipal;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Patient;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.UUID;

public abstract class AbstractPatientResource {

    protected AbstractPatientResource() {
        // Not used
    }

    @GET
    public abstract Bundle getPatients(OrganizationPrincipal organization, String patientMBI);

    @POST
    public abstract Patient submitPatient(OrganizationPrincipal organization, Patient patient);

    @GET
    @Path("/{patientID}")
    public abstract Patient getPatient(UUID patientID);

    @DELETE
    @Path("/{patientID}")
    public abstract Response deletePatient(UUID patientID);

    @PUT
    @Path("/{patientID}")
    public abstract Patient updatePatient(UUID patientID, Patient patient);
}