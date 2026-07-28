import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { Destination, DestinationService } from './destination.service';

/**
 * Destinations screen: list (GET /destinations) plus create and delete
 * actions. Every successful mutation reloads the list from the server
 * instead of mutating the local signal directly.
 *
 * No edit: travel-service exposes no PATCH/PUT on Destination.
 */
@Component({
  selector: 'app-destination-list',
  imports: [FormsModule],
  templateUrl: './destination-list.component.html',
})
export class DestinationListComponent implements OnInit {
  private readonly destinationService = inject(DestinationService);

  protected readonly destinations = signal<Destination[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  // Create form state.
  protected createName = '';
  protected createCountry = '';
  protected readonly creating = signal(false);
  protected readonly createError = signal<string | null>(null);

  // Delete state.
  protected readonly deletingDestinationId = signal<string | null>(null);
  protected readonly deleteError = signal<string | null>(null);

  ngOnInit(): void {
    this.loadDestinations();
  }

  private loadDestinations(): void {
    this.loading.set(true);
    this.error.set(null);
    this.destinationService.list().subscribe({
      next: (destinations) => {
        this.destinations.set(destinations);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger la liste des destinations.');
        this.loading.set(false);
      },
    });
  }

  createDestination(): void {
    this.creating.set(true);
    this.createError.set(null);

    this.destinationService.create(this.createName, this.createCountry).subscribe({
      next: () => {
        this.creating.set(false);
        this.createName = '';
        this.createCountry = '';
        this.loadDestinations();
      },
      error: (err: HttpErrorResponse) => {
        this.creating.set(false);
        this.createError.set(this.extractErrorMessage(err, 'Impossible de créer cette destination.'));
      },
    });
  }

  deleteDestination(destination: Destination): void {
    if (!confirm(`Supprimer la destination ${destination.name} ?`)) {
      return;
    }

    this.deletingDestinationId.set(destination.id);
    this.deleteError.set(null);

    this.destinationService.delete(destination.id).subscribe({
      next: () => {
        this.deletingDestinationId.set(null);
        this.loadDestinations();
      },
      error: (err: HttpErrorResponse) => {
        this.deletingDestinationId.set(null);
        this.deleteError.set(this.extractErrorMessage(err, 'Impossible de supprimer cette destination.'));
      },
    });
  }

  private extractErrorMessage(err: HttpErrorResponse, fallback: string): string {
    return typeof err.error?.error === 'string' ? err.error.error : fallback;
  }
}