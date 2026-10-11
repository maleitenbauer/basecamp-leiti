export interface ReportStat {
	label: string;
	value: string;
	detail: string | null;
}

export interface ReportBar {
	label: string;
	value: number;
	display: string;
	/** A day that hasn't happened yet (in a week that's still going on). */
	future: boolean;
}

export interface ReportBars {
	title: string;
	bars: ReportBar[];
	/** Drawn as a line across the chart, e.g. a daily calorie limit. */
	goal: number | null;
}

/** One module's part of the report. The page renders any section it gets, so a new module needs no frontend work. */
export interface ReportSection {
	id: string;
	title: string;
	/** The module's id in the registry, for its icon. */
	moduleId: string;
	headline: string;
	/** 0..1, how it went; null when there's no natural measure of success. */
	score: number | null;
	stats: ReportStat[];
	bars: ReportBars | null;
	highlights: string[];
}

export interface WeeklyReport {
	weekStart: string;
	weekEnd: string;
	today: string;
	/** The week isn't over yet, so the figures cover only the days that have happened. */
	inProgress: boolean;
	/** 0..1 average of the sections that have a score; null when none do. */
	score: number | null;
	sections: ReportSection[];
}
