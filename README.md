# Introduction:

This research explores queueing models in the context of Emergency Departments (EDs). Emergency Departments face hard-to-predict patient arrivals, varying service times, and limited resources, often resulting in congestion and delays. By applying queueing theory, we aim to analyze patient flow, identify bottlenecks, and evaluate strategies to improve efficiency and patient care. This simulation provides insights into optimizing resource allocation and reducing wait times in ED settings.  

# Architecture
The system has three main stages to modularize: 
## Modules

- **SortingNurse + Registration + Triage:** Reception
- **Waiting Room:** Waiting Room
- **Zone(ER Bed):** Zone
- Each module:
    - Has a queue of incoming jobs (patients)
    - Has an output that may:
        - Send the job to the next module(modules are interdependent)
        - Discharge the job(when is the final module) 
    - Follows queueing models (e.g., M/M/1, M/M/C, Kendall notation)

## Entities

- **WaitingRoom**
    - Contains a queue where jobs wait
    - Follows a specific service order (e.g., FCFS)
    - Has an attendant (Dispatcher)
- **Dispatcher**
    - Enforces service orders for the subsystem
    - When a server requests a job, ensures the correct job is selected
    - Tracks resources whose next jobs are in this room
        - Resources may be ordered by:
            - Expected mean response time
            - Next request job event timing
    - For pre-emptible resources:
        - Tracks jobs under service for each resource
        - If a higher priority job arrives:
            - Sends it to the pre-emptible resource
            - Resource must be ready to accept it

- **Resources (Servers)**
    - Examples:
        - Nurses
        - ED Physicians
        - Beds
        - etc.
    - At any time, a resource is:
        - Occupied, or
        - Not occupied
    - Occupation can be:
        - Preemptive, or
        - Non-preemptive
    - For pre-emptible resources:
        - When notified of a higher priority job:
            - Releases the running job
            - Accepts the new assigned job
            - Tracks status of pre-emptively released jobs
    - Availability(working hours per unit time. e.g: night shit, 8AM to 10PM):
        - Complete
        - Partial
            - Available for a specific time
            - Might be on-call for emergencies

- **Patient**
    - Has an ESI level
    - Has arrival time
    - Has arrival method (walk-in/by-ambulance)
    - Expected Length Of Stay(LOS)[Response Time] based on:
        - ESI
        - Age
        - Gender
        - Referral
        - Arrival method
        - (cite as needed)
        - etc
    - Has Likelihood to abandon at any stage based on:
        - Identities like age, ESI, Arrival method, has referral, economic class,...
    - Has identities:
        - Assigned number (upon arrival or after triage)
        - age
        - Gender
        - hasReferral
        - etc
        - Other identities (e.g., names) may not be necessary for simulation

## Relevant Quantities & Metrics

- **General**
    - Arrival rate
    - Interarrival time (I)
        - Can be i.i.d. random variable
        - Mean interarrival time \( E[I] = 1/\lambda \)
    - Service order
    - Expected waiting time,delay (TQ)
    - Number of jobs in queue (NQ)

- **Resources**
    - Expected arrival rate
    - Expected service rate
    - Throughput
    - Utilization



# Experiment:
## Methods:
The simulation operates over a defined period, typically 365 days, with a 30-day warm-up period to achieve steady-state conditions. Patient arrivals are dynamic, with rates varying hourly to reflect real-world ED demand fluctuations. Staffing levels for treatment zones are also dynamic, adjusting based on the time of day. Patients in queues or waiting for staff are prioritized using a "Higher Acuity First" policy, breaking ties by earliest arrival time. Patients can Leave Without Being Seen (LWBS) or die, based on calculated probabilities influenced by congestion.

## Experimental Models
Three models were simulated to assess the impact of misdiagnosis and reassessment:

- **Model 1: Base Model (No Misdiagnosis, No Reassessment)**
- Ideal scenario where patient triage is consistently accurate. 

-**Model 2: Misdiagnosis Enabled, No Reassessment**
- Description: Building upon the Base Model, this iteration introduces the possibility of misdiagnosis during the triage process.
- Misdiagnosis Mechanism: The probability of misdiagnosis at Triage is dynamic, influenced by: Time of Day, ED Congestion, Patient, Complexity, and
- What I’m Changing: Standard Deviation Multiplier-  This parameter directly scales the variability in assigned ESI levels and increases the base rate of misdiagnosis.
- Impact of Misdiagnosis: Penalty factor on their service time in the treatment zones, increased with the magnitude of the misdiagnosis. Base misdiagnosis rate (StdDevMult = 0) is 4.5%.
-**Model 3: Misdiagnosis and Reassessment Enabled**
  - Description: Integrates a "Reassessment Zone" into the ED flow to mitigate the effects of misdiagnosis.
  - Reassessment Trigger: Patients entering a treatment zone are reassessed after an assigned delay (0-240 min) if still present in zone queues to catch misdiagnosed/delayed patients and rerouted.
  - Re-routing: Following reassessment, patients are re-routed to the appropriate primary treatment zone based on their corrected true ESI level, following the same assignment logic as in Model 1 and 2. If rerouted, misdiagnosis penalty is lowered. 

## Question: (1) How does reassessing patients after queue delay affect length of stay and death rates? (2) Which reassessment delay is optimal for reducing length of stay while maintaining LWBS rates and death count?


