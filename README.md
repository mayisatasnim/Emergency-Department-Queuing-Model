Introduction:
This research explores queueing models in the context of Emergency Departments (EDs). Emergency Departments face hard-to-predict patient arrivals, varying service times, and limited resources, often resulting in congestion and delays. By applying queueing theory, we aim to analyze patient flow, identify bottlenecks, and evaluate strategies to improve efficiency and patient care. This simulation provides insights into optimizing resource allocation and reducing wait times in ED settings.

Architecture
The system has three main stages to modularize:

Modules
Sort Nurse + Registration + Triage: Reception
Waiting Room: Waiting Room
Zone(ER Bed): Zone
Each module:
Has a queue of incoming jobs (patients)
Has an output that may:
Send the job to the next module(modules are interdependent)
Discharge the job(when is the final module)
Follows queueing models (e.g., M/M/1, M/M/C, Kendall notation)
Entities
WaitingRoom

Contains a queue where jobs wait
Follows a specific service order (e.g., FCFS)
Has an attendant (Dispatcher)
Dispatcher

Enforces service orders for the subsystem
When a server requests a job, ensures the correct job is selected
Tracks resources whose next jobs are in this room
Resources may be ordered by:
Expected mean response time
Next request job event timing
For pre-emptible resources:
Tracks jobs under service for each resource
If a higher priority job arrives:
Sends it to the pre-emptible resource
Resource must be ready to accept it
Resources (Servers)

Examples:
Nurses
ED Physicians
Beds
etc.
At any time, a resource is:
Occupied, or
Not occupied
Occupation can be:
Preemptive, or
Non-preemptive
For pre-emptible resources:
When notified of a higher priority job:
Releases the running job
Accepts the new assigned job
Tracks status of pre-emptively released jobs
Availability(working hours per unit time. e.g: night shit, 8AM to 10PM):
Complete
Partial
Available for a specific time
Might be on-call for emergencies
Patient

Has an ESI level
Has arrival time
Has arrival method (walk-in/by-ambulance)
Expected Length Of Stay(LOS)[Response Time] based on:
ESI
Age
Gender
Referral
Arrival method
(cite as needed)
etc
Has Likelihood to abandon at any stage based on:
Identities like age, ESI, Arrival method, has referral, economic class,...
Has identities:
Assigned number (upon arrival or after triage)
age
Gender
hasReferral
etc
Other identities (e.g., names) may not be necessary for simulation
Relevant Quantities & Metrics
General

Arrival rate
Interarrival time (I)
Can be i.i.d. random variable
Mean interarrival time ( E[I] = 1/\lambda )
Service order
Expected waiting time,delay (TQ)
Number of jobs in queue (NQ)
Resources

Expected arrival rate
Expected service rate
Throughput
Utilization
