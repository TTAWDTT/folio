export {};
declare global { interface Window { acceptancePost?: (json: string) => Promise<void> } }
