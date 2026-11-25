export type EndedBy = 'MANUAL' | 'AUTO';

export interface PlanExercise {
	id: number;
	name: string;
	sets: number;
	reps: number;
	weightKg: number | null;
	restSeconds: number | null;
	note: string | null;
}

export interface Plan {
	id: number;
	name: string;
	note: string | null;
	routineId: number | null;
	routineName: string | null;
	exercises: PlanExercise[];
	timesDone: number;
	lastDoneOn: string | null;
}

export interface PlanExerciseInput {
	name: string;
	sets: number;
	reps: number;
	weightKg: number | null;
	restSeconds: number | null;
	note: string | null;
}

export interface PlanInput {
	name: string;
	note: string | null;
	routineId: number | null;
	exercises: PlanExerciseInput[];
}

export interface SetValues {
	reps: number;
	weightKg: number | null;
}

export interface WorkoutSet extends SetValues {
	id: number;
	doneAt: string;
}

export interface WorkoutExercise {
	id: number;
	name: string;
	targetSets: number | null;
	targetReps: number | null;
	targetWeightKg: number | null;
	restSeconds: number | null;
	sets: WorkoutSet[];
	/** What was done the last time this exercise was done. */
	previous: { day: string; sets: SetValues[] } | null;
}

export interface Workout {
	id: number;
	planId: number | null;
	name: string;
	day: string;
	startedAt: string;
	finishedAt: string | null;
	endedBy: EndedBy | null;
	/** When this workout is ended automatically if it is still running. */
	autoFinishAt: string;
	exercises: WorkoutExercise[];
}

export interface WorkoutSummary {
	id: number;
	name: string;
	day: string;
	startedAt: string;
	minutes: number;
	exercises: number;
	sets: number;
	volumeKg: number;
	endedBy: EndedBy | null;
}

export interface TrainingStats {
	weekStart: string;
	workoutsThisWeek: number;
	weeklyTarget: number | null;
	daysSinceLast: number | null;
	totalWorkouts: number;
}

export interface TrainingOverview {
	active: Workout | null;
	plans: Plan[];
	stats: TrainingStats;
	recent: WorkoutSummary[];
}

export interface PersonalBest {
	exercise: string;
	weightKg: number | null;
	reps: number;
}

export interface FinishResult {
	summary: WorkoutSummary;
	personalBests: PersonalBest[];
	/** Name of the routine that was ticked off, if the plan is linked to one. */
	routineCompleted: string | null;
	stats: TrainingStats;
}
